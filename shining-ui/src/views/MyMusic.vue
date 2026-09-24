<template>
  <div class="my-music-container">
    <h2>我的音乐</h2>
    <div v-if="!userId" class="empty-tip">
      请先登录查看你的音乐内容
    </div>
    <div v-else>
      <div v-if="!offlineSelected" class="quick-links">
        <div class="quick-link-card" @click="$router.push('/play-history')">
          <span class="ql-icon">&#9654;</span>
          <span>播放历史</span>
        </div>
        <div class="quick-link-card" @click="$router.push('/ranking')">
          <span class="ql-icon">&#9733;</span>
          <span>热门排行</span>
        </div>
        <div class="quick-link-card" @click="$router.push('/annual-report/story')">
          <span class="ql-icon">&#128202;</span>
          <span>年度报告</span>
        </div>
      </div>
      <section class="favorites-section">
        <div class="section-header">
          <h3>{{ offlineSelected ? '本机已缓存歌曲' : '我的收藏' }}</h3>
          <div class="favorites-actions">
            <div v-if="!isAndroidApp && !offlineSelected" class="view-switch" role="group" aria-label="收藏歌曲展示方式">
              <button type="button" :class="{ active: favoriteView === 'cover' }"
                :aria-pressed="favoriteView === 'cover'" @click="setFavoriteView('cover')">封面</button>
              <button type="button" :class="{ active: favoriteView === 'compact' }"
                :aria-pressed="favoriteView === 'compact'" @click="setFavoriteView('compact')">简洁</button>
            </div>
            <button class="refresh-btn" @click="loadFavorites" :disabled="loading">
              {{ loading ? '加载中...' : '刷新' }}
            </button>
          </div>
        </div>
        <div v-if="loading" class="empty-tip">{{ offlineSelected ? '正在读取本机缓存，请稍候' : '正在加载收藏歌曲，请稍候' }}</div>
        <div v-else-if="favorites.length === 0" class="empty-tip">
          {{ offlineSelected ? '当前账户没有完整的本机缓存歌曲' : '你还没有收藏任何歌曲' }}
        </div>
        <p v-if="offlineSelected && offlineError" class="empty-tip" role="alert">{{ offlineError }}</p>
        <div v-else class="songs-list" :class="{ compact: !isAndroidApp && !offlineSelected && favoriteView === 'compact' }">
          <div
            v-for="(song, index) in favorites"
            :key="song.id"
            class="song-card"
            :class="{ compact: !isAndroidApp && !offlineSelected && favoriteView === 'compact' }"
            @click="goToSong(song.id)"
          >
            <span v-if="!isAndroidApp && !offlineSelected && favoriteView === 'compact'" class="song-index" aria-hidden="true">{{ index + 1 + (favoritePage.size > 0 ? (favoritePage.page - 1) * favoritePage.size : 0) }}</span>
            <img v-else :src="song.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" />
            <div class="song-info">
              <h3>{{ song.title || '未知歌曲' }}</h3>
              <p>
                歌手：
                {{
                  artistNameMap[song.artistId] ||
                    (song.artistId ? `歌手 ${song.artistId}` : '未知')
                }}
              </p>
            </div>
            <button v-if="offlineSelected" class="favorite-btn" type="button"
              :disabled="offlineQueue.includes(String(song.id))"
              @click.stop="addOfflineSong(song.id)"
              :title="offlineQueue.includes(String(song.id)) ? '已在离线播放列表' : '加入离线播放列表'"
              :aria-label="offlineQueue.includes(String(song.id)) ? '已在离线播放列表' : '加入离线播放列表'"
            >{{ offlineQueue.includes(String(song.id)) ? '✓' : '＋' }}</button>
            <button v-else
              class="favorite-btn"
              :class="{ active: song.favorite }"
              @click.stop="toggleFavorite(song)"
              title="从收藏中移除"
            >
              <span class="heart-icon"></span>
            </button>
          </div>
        </div>
        <WebListPager v-if="!offlineSelected" v-bind="favoritePage" @change="changeFavoritePage" />
      </section>

      <section class="playlists-section">
        <div class="section-header">
          <h3>我的歌单</h3>
          <button class="refresh-btn" @click="loadMyPlaylists" :disabled="loadingPlaylists">
            {{ loadingPlaylists ? '加载中...' : '刷新' }}
          </button>
        </div>
        <div v-if="loadingPlaylists" class="empty-tip">
          正在加载歌单，请稍候
        </div>
        <div v-else-if="myPlaylists.length === 0" class="empty-tip">
          {{ offlineSelected ? '本机离线播放列表不可用' : '你还没有创建任何歌单' }}
        </div>
        <div v-else class="playlists-list">
          <div
            v-for="playlist in myPlaylists"
            :key="playlist.id"
            class="playlist-card"
            @click="goToPlaylist(playlist.id)"
          >
            <img
              :src="playlist.coverUrl || defaultCover"
              class="playlist-cover"
              alt="歌单封面"
            />
            <div class="playlist-info">
              <h3>{{ playlist.name || '未知歌单' }}</h3>
              <p>{{ playlist.description || '暂无简介' }}</p>
            </div>
          </div>
        </div>
        <p v-if="offlineSelected" class="empty-tip">离线模式只读取本机独立播放列表；原有线上歌单未缓存到手机，不会自动同步。</p>
        <WebListPager v-if="!offlineSelected" v-bind="myPlaylistPage" @change="changeMyPlaylistPage" />
      </section>

      <section v-if="!offlineSelected && userProfile" class="profile-section">
        <div class="section-header">
          <h3>听歌报告</h3>
          <button class="refresh-btn" @click="refreshProfile" :disabled="profileLoading">
            {{ profileLoading ? '更新中...' : '刷新画像' }}
          </button>
        </div>
        <div class="profile-grid">
          <div class="profile-card">
            <div class="profile-value">{{ userProfile.totalPlayCount || 0 }}</div>
            <div class="profile-label">总播放次数</div>
          </div>
          <div class="profile-card">
            <div class="profile-value">{{ formatDuration(userProfile.totalPlayDuration || 0) }}</div>
            <div class="profile-label">累计听歌时长</div>
          </div>
          <div class="profile-card">
            <div class="profile-value">{{ userProfile.dailyAvgPlays || 0 }}</div>
            <div class="profile-label">日均播放</div>
          </div>
          <div class="profile-card">
            <div class="profile-value">{{ userProfile.avgCompletionRate || 0 }}%</div>
            <div class="profile-label">平均完播率</div>
          </div>
          <div class="profile-card" v-if="userProfile.activeHour != null">
            <div class="profile-value">{{ userProfile.activeHour }}:00</div>
            <div class="profile-label">最活跃时段</div>
          </div>
          <div class="profile-card" v-if="topSingerName">
            <div class="profile-value">{{ topSingerName }}</div>
            <div class="profile-label">最爱歌手</div>
          </div>
        </div>
      </section>

      <section v-if="!offlineSelected" class="statistics-section">
        <div class="section-header statistics-header">
          <h3>最多播放</h3>
          <div class="dimension-tabs">
            <button
              v-for="option in topSongDimensions"
              :key="option.value"
              class="dimension-tab"
              :class="{ active: option.value === selectedTopSongDimension }"
              @click="changeTopSongDimension(option.value)"
            >
              {{ option.label }}
            </button>
          </div>
        </div>
        <div v-if="topSongsLoading" class="empty-tip">正在加载播放统计...</div>
        <div v-else-if="topSongs.length === 0" class="empty-tip">
          暂无播放统计数据
        </div>
        <ul v-else class="top-song-list">
          <li v-for="(item, index) in topSongs" :key="item.songId || index" class="top-song-item">
            <div class="top-song-index">{{ index + 1 }}</div>
            <img
              :src="item.song.coverUrl || defaultCover"
              class="top-song-cover"
              alt="歌曲封面"
            />
            <div class="top-song-info">
              <p class="top-song-title">{{ item.song.title || `歌曲 ${item.songId}` }}</p>
              <p class="top-song-meta">
                播放 {{ item.playCount || 0 }} 次 ·
                {{
                  item.song.artistName ||
                  (item.song.artistId ? `歌手 ${item.song.artistId}` : '未知歌手')
                }}
              </p>
            </div>
            <button class="top-song-btn" @click="goToSong(item.songId)">去播放</button>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<script>
import musicApi from '@/api/music';
import statisticsApi from '@/api/statistics';
import defaultCover from '@/assets/default-cover.png';
import { isOfflineSelected, localQueue, localSongs, localPlaylist } from '@/offline/localLibrary';
import { isAndroidApp } from '@/utils/androidServer';
import { webListMode, setWebListMode } from '@/utils/webListMode';
import WebListPager from '@/components/WebListPager.vue';
import { initialPage, pageParams, pageItems, pageTotal, paginateLocal } from '@/utils/listPagination';

export default {
  name: 'MyMusic',
  components: { WebListPager },
  data() {
    return {
      offlineSelected: isOfflineSelected(),
      isAndroidApp,
      offlineQueue: [],
      offlineError: '',
      libraryLoadEpoch: 0,
      favorites: [],
      favoritePage: initialPage(),
      myPlaylistPage: initialPage(),
      favoritesRequest: 0,
      playlistsRequest: 0,
      loading: false,
      userId: null,
      myPlaylists: [],
      loadingPlaylists: false,
      defaultCover,
      artistNameMap: {},
      topSongs: [],
      topSongsLoading: false,
      topSongDimensions: [
        { label: '今日', value: 'TODAY' },
        { label: '近7天', value: 'WEEK' },
        { label: '近30天', value: 'MONTH' },
        { label: '全部', value: 'TOTAL' },
      ],
      selectedTopSongDimension: 'WEEK',
      userProfile: null,
      profileLoading: false,
      topSingerName: null,
    };
  },
  computed: {
    favoriteView() {
      return webListMode.value;
    },
  },
  created() {
    window.addEventListener('offlineModeChanged', this.onOfflineModeChanged);
    this.$bus.on('offlineLibrary:state', this.onOfflineLibraryState);
    let userBase = {};
    try { userBase = JSON.parse(localStorage.getItem('userBase') || '{}'); } catch (e) { /* ignore */ }
    this.userId = userBase.id ?? null;
    if (this.offlineSelected) {
      this.offlineQueue = localQueue(this.userId);
      this.loadFavorites();
      this.loadMyPlaylists();
    } else if (this.userId) {
      this.loadFavorites();
      this.loadMyPlaylists();
      this.loadTopSongs();
      this.loadUserProfile();
    }
  },
  beforeUnmount() {
    this.libraryLoadEpoch++;
    window.removeEventListener('offlineModeChanged', this.onOfflineModeChanged);
    this.$bus.off('offlineLibrary:state', this.onOfflineLibraryState);
  },
  methods: {
    changeFavoritePage(next) {
      this.favoritePage = { ...this.favoritePage, ...next };
      this.loadFavorites();
    },
    changeMyPlaylistPage(next) {
      this.myPlaylistPage = { ...this.myPlaylistPage, ...next };
      this.loadMyPlaylists();
    },
    setFavoriteView(view) {
      setWebListMode(view);
    },
    onOfflineLibraryState(state) {
      if (!this.offlineSelected || !state?.offlineMode || String(state.accountId) !== String(this.userId)) return;
      this.offlineQueue = state.queue.map(String);
      this.myPlaylists = [localPlaylist(this.userId)];
      if (state.error) this.offlineError = state.error;
    },
    offlineCommand(action, songId) {
      this.offlineError = '';
      this.$bus.emit('offlineLibrary:command', { accountId: String(this.userId), action, songId });
    },
    addOfflineSong(songId) { this.offlineCommand('add', songId); },
    onOfflineModeChanged() {
      const previous = this.offlineSelected;
      this.offlineSelected = isOfflineSelected();
      this.libraryLoadEpoch++;
      if (!previous && this.offlineSelected) {
        this.offlineQueue = localQueue(this.userId);
        this.loadFavorites();
        this.loadMyPlaylists();
      }
      if (previous && !this.offlineSelected && this.userId) {
        this.loadFavorites();
        this.loadMyPlaylists();
        this.loadTopSongs();
        this.loadUserProfile();
      }
    },
    async loadFavorites() {
      if (!this.userId) {
        return;
      }
      const epoch = this.libraryLoadEpoch;
      const request = ++this.favoritesRequest;
      if (this.offlineSelected) {
        this.loading = true;
        this.offlineError = '';
        try {
          const songs = await localSongs(this.userId);
          if (this.offlineSelected && epoch === this.libraryLoadEpoch) {
            this.favorites = songs;
            this.artistNameMap = Object.fromEntries(songs.map(song => [song.artistId, song.artistName]));
          }
        } catch (error) {
          if (epoch === this.libraryLoadEpoch) this.offlineError = `读取本机歌曲失败：${error.message}`;
        } finally {
          if (epoch === this.libraryLoadEpoch) this.loading = false;
        }
        return;
      }
      this.loading = true;
      try {
        const response = await musicApi.getUserFavoriteSongs(this.userId, pageParams(this.favoritePage));
        if (this.offlineSelected || epoch !== this.libraryLoadEpoch || request !== this.favoritesRequest) return;
        if (response.data && response.data.passed) {
          this.favorites = pageItems(response, this.favoritePage);
          this.favoritePage.total = pageTotal(response);
          await this.loadFavoriteArtistNames();
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('获取收藏列表失败：' + msg);
        }
      } catch (error) {
        if (!this.offlineSelected && epoch === this.libraryLoadEpoch) alert('获取收藏列表失败：' + error.message);
      } finally {
        if (epoch === this.libraryLoadEpoch && request === this.favoritesRequest) this.loading = false;
      }
    },
    async loadFavoriteArtistNames() {
      if (this.offlineSelected) return;
      const ids = Array.from(
        new Set(
          this.favorites
            .map(song => song.artistId)
            .filter(id => id !== null && id !== undefined && id !== '')
        )
      ).filter(id => !(id in this.artistNameMap));

      if (!ids.length) {
        return;
      }

      await Promise.all(ids.map(id => this.fetchArtistName(id)));
    },
    async loadMyPlaylists() {
      if (!this.userId) {
        return;
      }
      if (this.offlineSelected) {
        this.offlineQueue = localQueue(this.userId);
        this.myPlaylists = [localPlaylist(this.userId)];
        return;
      }
      const epoch = this.libraryLoadEpoch;
      const request = ++this.playlistsRequest;
      this.loadingPlaylists = true;
      try {
        const response = await musicApi.discoverPlaylists(this.userId,
          { ...pageParams(this.myPlaylistPage), ownerOnly: true });
        if (this.offlineSelected || epoch !== this.libraryLoadEpoch || request !== this.playlistsRequest) return;
        if (response.data && response.data.passed) {
          const mine = pageItems(response).filter(p => String(p.userId) === String(this.userId));
          if (Array.isArray(response.data.data)) {
            // The currently running backend may still return the legacy array.
            this.myPlaylistPage.total = mine.length;
            this.myPlaylists = paginateLocal(mine, this.myPlaylistPage);
          } else {
            this.myPlaylistPage.total = pageTotal(response);
            this.myPlaylists = mine;
          }
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('获取歌单列表失败：' + msg);
        }
      } catch (error) {
        if (!this.offlineSelected && epoch === this.libraryLoadEpoch) alert('获取歌单列表失败：' + error.message);
      } finally {
        if (epoch === this.libraryLoadEpoch && request === this.playlistsRequest) this.loadingPlaylists = false;
      }
    },
    async loadTopSongs() {
      if (!this.userId || this.offlineSelected) {
        return;
      }
      this.topSongsLoading = true;
      try {
        const response = await statisticsApi.getUserTopSongs(this.userId, {
          dimension: this.selectedTopSongDimension,
          limit: 5,
        });
        if (this.offlineSelected) return;
        if (response.data && response.data.passed) {
          const list = response.data.data || [];
          const enriched = await Promise.all(
            list.map(async item => {
              const songInfo = await this.fetchSongBaseInfo(item.songId);
              return {
                songId: item.songId,
                playCount: item.playCount,
                song: songInfo || {},
              };
            })
          );
          if (this.offlineSelected) return;
          this.topSongs = enriched;
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('获取播放统计失败：' + msg);
        }
      } catch (error) {
        if (!this.offlineSelected) alert('获取播放统计失败：' + error.message);
      } finally {
        this.topSongsLoading = false;
      }
    },
    changeTopSongDimension(value) {
      if (this.selectedTopSongDimension === value) {
        return;
      }
      this.selectedTopSongDimension = value;
      this.loadTopSongs();
    },
    async loadUserProfile() {
      if (this.offlineSelected) return;
      try {
        const res = await statisticsApi.getUserProfile(this.userId);
        if (this.offlineSelected) return;
        if (res.data?.passed && res.data.data) {
          this.userProfile = res.data.data;
          if (this.userProfile.topSingerId) {
            this.loadTopSingerName(this.userProfile.topSingerId);
          }
        }
      } catch (e) {
        console.warn('加载用户画像失败', e);
      }
    },
    async refreshProfile() {
      this.profileLoading = true;
      try {
        await statisticsApi.refreshUserProfile(this.userId);
        await this.loadUserProfile();
      } catch (e) {
        alert('刷新画像失败：' + e.message);
      } finally {
        this.profileLoading = false;
      }
    },
    async loadTopSingerName(singerId) {
      if (this.offlineSelected) return;
      try {
        const res = await musicApi.getSingerBaseInfo(singerId);
        if (this.offlineSelected) return;
        if (res.data?.passed && res.data.data) {
          this.topSingerName = res.data.data.name || `歌手 ${singerId}`;
        }
      } catch (e) {
        this.topSingerName = `歌手 ${singerId}`;
      }
    },
    formatDuration(seconds) {
      if (!seconds || seconds <= 0) return '0分钟';
      const hours = Math.floor(seconds / 3600);
      const minutes = Math.floor((seconds % 3600) / 60);
      if (hours > 0) return `${hours}小时${minutes}分钟`;
      return `${minutes}分钟`;
    },
    async fetchArtistName(artistId) {
      if (this.offlineSelected) return this.artistNameMap[artistId] || `歌手 ${artistId}`;
      if (!artistId) {
        return '';
      }
      if (this.artistNameMap[artistId]) {
        return this.artistNameMap[artistId];
      }
      try {
        const res = await musicApi.getSingerBaseInfo(artistId);
        const name =
            res.data && res.data.passed && res.data.data && res.data.data.name
                ? res.data.data.name
                : `歌手 ${artistId}`;
        this.artistNameMap[artistId] = name;
        return name;
      } catch (error) {
        const fallback = `歌手 ${artistId}`;
        this.artistNameMap[artistId] = fallback;
        return fallback;
      }
    },
    async fetchSongBaseInfo(songId) {
      if (!songId || this.offlineSelected) {
        return {};
      }
      try {
        const res = await musicApi.getSongBaseInfo(songId, this.userId);
        if (this.offlineSelected) return {};
        if (res.data && res.data.passed) {
          const song = res.data.data || {};
          if (song.artistId) {
            song.artistName = await this.fetchArtistName(song.artistId);
          }
          return song;
        }
      } catch (error) {
        console.warn('获取歌曲信息失败', songId, error);
      }
      return {};
    },
    async toggleFavorite(song) {
      if (!song || !song.id) {
        return;
      }
      if (!this.userId) {
        alert('请先登录后再管理收藏');
        return;
      }
      try {
        const response = await musicApi.toggleFavoriteSong({
          userId: this.userId,
          songId: song.id,
        });
        if (response.data && response.data.passed) {
          const favorite = response.data.data?.favorite ?? false;
          song.favorite = favorite;
          if (!favorite) {
            this.favorites = this.favorites.filter(item => item.id !== song.id);
            if (this.favoritePage.size > 0 && this.favoritePage.page > 1 && !this.favorites.length) {
              this.favoritePage.page--;
            }
            await this.loadFavorites();
          }
          statisticsApi.reportEvent({
            userId: this.userId,
            eventType: 'FAVORITE',
            targetType: 'song',
            targetId: song.id,
            extraData: { action: favorite ? 'add' : 'remove' },
          }).catch(() => {});
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('更新收藏状态失败：' + msg);
        }
      } catch (error) {
        alert('更新收藏状态失败：' + error.message);
      }
    },
    goToSong(songId) {
      if (this.offlineSelected) {
        this.offlineCommand('addAndPlay', songId);
        return;
      }
      this.$router.push(`/song/${songId}`);
    },
    goToPlaylist(playlistId) {
      if (this.offlineSelected) return this.$router.push('/playlist/local');
      this.$router.push(`/playlist/${playlistId}`);
    },
  },
};
</script>

<style scoped>
.my-music-container {
  padding: 24px;
  max-width: 1100px;
  margin: 0 auto;
  background: linear-gradient(to bottom, #e0f7fa, #ffffff);
}
h2 {
  margin-bottom: 20px;
  text-align: center;
}
.favorites-section {
  background: rgba(255, 255, 255, 0.85);
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 10px 25px rgba(15, 23, 42, 0.08);
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.favorites-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.view-switch {
  display: inline-flex;
  padding: 3px;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  background: #f1f5f9;
}
.view-switch button {
  border: 0;
  border-radius: 999px;
  padding: 5px 12px;
  background: transparent;
  color: #475569;
  cursor: pointer;
}
.view-switch button.active {
  background: #fff;
  color: #0369a1;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.12);
}
.view-switch button:focus-visible {
  outline: 2px solid #0284c7;
  outline-offset: 2px;
}
.refresh-btn {
  padding: 6px 16px;
  border-radius: 999px;
  border: none;
  background: linear-gradient(120deg, #4facfe, #00f2fe);
  color: #fff;
  cursor: pointer;
  font-size: 14px;
}
.refresh-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.empty-tip {
  text-align: center;
  color: #64748b;
  padding: 40px 0;
}
.songs-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 20px;
}
.songs-list.compact {
  grid-template-columns: minmax(0, 1fr);
  gap: 2px;
}
.song-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
  padding: 16px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  position: relative;
}
.song-card.compact {
  display: grid;
  box-sizing: border-box;
  grid-template-columns: 24px minmax(0, 1fr) 30px;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 5px 10px;
  border-radius: 4px;
  box-shadow: none;
}
.song-card.compact:hover {
  transform: none;
  background: #f1f5f9;
  box-shadow: none;
}
.song-index {
  text-align: center;
  color: #64748b;
  font-variant-numeric: tabular-nums;
}
.song-card.compact .song-index {
  font-size: 12px;
}
.song-card.compact .song-info {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
}
.song-card.compact .song-info h3 {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 14px;
  line-height: 20px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.song-card.compact .song-info p {
  flex: 0 1 35%;
  min-width: 0;
  margin: 0;
  font-size: 12px;
  line-height: 20px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.song-card.compact .favorite-btn {
  position: static;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #f8fafc;
  box-shadow: none;
}
.song-card.compact .favorite-btn .heart-icon {
  width: 16px;
  height: 16px;
}
.song-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 26px rgba(15, 23, 42, 0.12);
}
.song-cover {
  width: 100%;
  height: 150px;
  object-fit: cover;
  border-radius: 10px;
  margin-bottom: 12px;
}
.song-info h3 {
  margin: 0;
  font-size: 18px;
}
.song-info p {
  margin: 4px 0 0;
  color: #666;
  font-size: 14px;
}
.favorite-btn {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 42px;
  height: 42px;
  border-radius: 21px;
  border: none;
  background: rgba(255, 255, 255, 0.8);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s, box-shadow 0.2s, background 0.2s;
}
.favorite-btn .heart-icon {
  width: 22px;
  height: 22px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23ff6b81' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M12 21s-6.3-4.35-9-8.4C1 9 2 5 5.5 4S12 8 12 8s2.5-4 6-4 4.5 3 2.5 6.6c-2.7 4.05-9 8.4-9 8.4z'/%3E%3C/svg%3E");
  background-size: contain;
  background-repeat: no-repeat;
}
.favorite-btn.active {
  background: rgba(255, 99, 132, 0.18);
  box-shadow: 0 6px 16px rgba(255, 99, 132, 0.35);
}
.favorite-btn.active .heart-icon {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23ff3366'%3E%3Cpath d='M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3c3.08 0 5.5 2.42 5.5 5.5 0 3.78-3.4 6.86-8.55 11.54L12 21.35z'/%3E%3C/svg%3E");
}
.favorite-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 20px rgba(255, 99, 132, 0.3);
}
.playlists-section {
  margin-top: 24px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 10px 25px rgba(15, 23, 42, 0.08);
}
.statistics-section {
  margin-top: 24px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.1);
}
.statistics-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.dimension-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.dimension-tab {
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  background: transparent;
  color: #475569;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}
.dimension-tab.active {
  background: linear-gradient(120deg, #4facfe, #38bdf8);
  color: #fff;
  border-color: transparent;
  box-shadow: 0 6px 16px rgba(56, 189, 248, 0.35);
}
.top-song-list {
  list-style: none;
  margin: 16px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.top-song-item {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 12px;
  padding: 12px 16px;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
}
.top-song-index {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff9a9e, #fad0c4);
  color: #fff;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.top-song-cover {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid rgba(148, 163, 184, 0.3);
}
.top-song-info {
  display: flex;
  flex-direction: column;
  flex: 1;
}
.top-song-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}
.top-song-meta {
  margin: 2px 0 0;
  color: #64748b;
  font-size: 13px;
}
.top-song-btn {
  padding: 6px 14px;
  border-radius: 999px;
  border: none;
  background: linear-gradient(120deg, #a855f7, #ec4899);
  color: #fff;
  cursor: pointer;
  font-size: 13px;
  transition: transform 0.2s, box-shadow 0.2s;
}
.top-song-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(236, 72, 153, 0.35);
}
.playlists-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 20px;
}
.playlist-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
  padding: 16px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}
.playlist-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 26px rgba(15, 23, 42, 0.12);
}
.playlist-cover {
  width: 100%;
  height: 150px;
  object-fit: cover;
  border-radius: 10px;
  margin-bottom: 12px;
}
.playlist-info h3 {
  margin: 0;
  font-size: 18px;
}
.playlist-info p {
  margin: 4px 0 0;
  color: #666;
  font-size: 14px;
}

.profile-section {
  margin-bottom: 32px;
}
.profile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 16px;
}
.profile-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 14px;
  padding: 20px 16px;
  text-align: center;
  color: #fff;
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.25);
  transition: transform 0.2s;
}
.profile-card:hover {
  transform: translateY(-3px);
}
.profile-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  margin-bottom: 6px;
}
.profile-label {
  font-size: 13px;
  opacity: 0.85;
}

.quick-links {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.quick-link-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border-radius: 12px;
  background: linear-gradient(135deg, #e0f2fe, #f0f9ff);
  border: 1px solid #bae6fd;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: #0369a1;
  transition: transform 0.15s, box-shadow 0.15s;
}

.quick-link-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(3, 105, 161, 0.15);
}

.ql-icon {
  font-size: 16px;
}
</style>
