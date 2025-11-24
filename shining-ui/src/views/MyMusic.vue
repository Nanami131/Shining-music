<template>
  <div class="my-music-container">
    <h2>我的音乐</h2>
    <div v-if="!userId" class="empty-tip">
      请先登录查看你的音乐内容
    </div>
    <div v-else>
      <section class="favorites-section">
        <div class="section-header">
          <h3>我的收藏</h3>
          <button class="refresh-btn" @click="loadFavorites" :disabled="loading">
            {{ loading ? '加载中...' : '刷新' }}
          </button>
        </div>
        <div v-if="loading" class="empty-tip">正在加载收藏歌曲，请稍候</div>
        <div v-else-if="favorites.length === 0" class="empty-tip">
          你还没有收藏任何歌曲
        </div>
        <div v-else class="songs-list">
          <div
            v-for="song in favorites"
            :key="song.id"
            class="song-card"
            @click="goToSong(song.id)"
          >
            <img :src="song.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" />
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
            <button
              class="favorite-btn"
              :class="{ active: song.favorite }"
              @click.stop="toggleFavorite(song)"
              title="从收藏中移除"
            >
              <span class="heart-icon"></span>
            </button>
          </div>
        </div>
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
          你还没有创建任何歌单
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
      </section>

      <section class="statistics-section">
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

export default {
  name: 'MyMusic',
  data() {
    return {
      favorites: [],
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
    };
  },
  created() {
    const userBase = JSON.parse(localStorage.getItem('userBase') || '{}');
    this.userId = userBase.id ?? null;
    if (this.userId) {
      this.loadFavorites();
      this.loadMyPlaylists();
      this.loadTopSongs();
    }
  },
  methods: {
    async loadFavorites() {
      if (!this.userId) {
        return;
      }
      this.loading = true;
      try {
        const response = await musicApi.getUserFavoriteSongs(this.userId);
        if (response.data && response.data.passed) {
          this.favorites = response.data.data || [];
          await this.loadFavoriteArtistNames();
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('获取收藏列表失败：' + msg);
        }
      } catch (error) {
        alert('获取收藏列表失败：' + error.message);
      } finally {
        this.loading = false;
      }
    },
    async loadFavoriteArtistNames() {
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

      const tasks = ids.map(async id => {
        try {
          const res = await musicApi.getSingerBaseInfo(id);
          if (res.data && res.data.passed && res.data.data) {
            const name = res.data.data.name || `歌手 ${id}`;
            this.$set ? this.$set(this.artistNameMap, id, name) : (this.artistNameMap[id] = name);
          } else {
            this.artistNameMap[id] = `歌手 ${id}`;
          }
        } catch (e) {
          this.artistNameMap[id] = `歌手 ${id}`;
        }
      });

      await Promise.all(tasks);
    },
    async loadMyPlaylists() {
      if (!this.userId) {
        return;
      }
      this.loadingPlaylists = true;
      try {
        const response = await musicApi.discoverPlaylists(this.userId);
        if (response.data && response.data.passed) {
          const all = response.data.data || [];
          this.myPlaylists = all.filter(p => p.userId === this.userId);
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('获取歌单列表失败：' + msg);
        }
      } catch (error) {
        alert('获取歌单列表失败：' + error.message);
      } finally {
        this.loadingPlaylists = false;
      }
    },
    async loadTopSongs() {
      if (!this.userId) {
        return;
      }
      this.topSongsLoading = true;
      try {
        const response = await statisticsApi.getUserTopSongs(this.userId, {
          dimension: this.selectedTopSongDimension,
          limit: 5,
        });
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
          this.topSongs = enriched;
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('获取播放统计失败：' + msg);
        }
      } catch (error) {
        alert('获取播放统计失败：' + error.message);
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
    async fetchSongBaseInfo(songId) {
      if (!songId) {
        return {};
      }
      try {
        const res = await musicApi.getSongBaseInfo(songId, this.userId);
        if (res.data && res.data.passed) {
          return res.data.data || {};
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
          }
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('更新收藏状态失败：' + msg);
        }
      } catch (error) {
        alert('更新收藏状态失败：' + error.message);
      }
    },
    goToSong(songId) {
      this.$router.push(`/song/${songId}`);
    },
    goToPlaylist(playlistId) {
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
  margin-bottom: 16px;
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
.song-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
  padding: 16px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  position: relative;
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
</style>
