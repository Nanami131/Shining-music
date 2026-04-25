<template>
  <div class="songs-page">
    <StormFrontRain />
    <div class="songs-shell">
      <div class="songs-container">
        <div class="search-bar">
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索歌曲名、歌手、歌词..."
            @keyup.enter="handleSearch"
            @focus="showHistory = true"
            @blur="hideHistoryDelayed"
          />
          <button v-if="searchKeyword" class="clear-btn" @click="clearSearch">×</button>
          <div v-if="showHistory && !searchKeyword && searchHistory.length" class="search-history-dropdown">
            <div class="search-history-header">
              <span>最近搜索</span>
            </div>
            <div
              v-for="(item, idx) in searchHistory"
              :key="idx"
              class="search-history-item"
              @mousedown.prevent="useHistoryKeyword(item.keyword)"
            >
              {{ item.keyword }}
              <span v-if="item.cnt > 1" class="search-count">{{ item.cnt }}次</span>
            </div>
          </div>
        </div>

        <!-- 搜索结果 -->
        <section v-if="searchResults !== null" class="section section-search">
          <h2>搜索结果</h2>
          <div v-if="searchResults.length" class="songs-list">
            <div
              v-for="item in searchResults"
              :key="item.songId"
              class="song-card"
              @click="goToSong(item.songId)"
            >
              <img :src="item.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" />
              <div class="song-info">
                <h3 v-html="highlightTitle(item)"></h3>
                <p v-html="highlightSinger(item)"></p>
              </div>
              <div v-if="hasLyricsHighlight(item)" class="lyrics-snippet">
                <span v-for="(frag, i) in getLyricsHighlights(item)" :key="i" v-html="frag"></span>
              </div>
              <div class="song-card-actions">
                <button
                  class="song-action-btn play-action-btn"
                  title="播放"
                  @click.stop="playSearchResult(item)"
                >
                  ▶
                </button>
                <button
                  class="song-action-btn add-action-btn"
                  :class="{ added: isSongInCurrentPlaylist(item.songId) }"
                  :title="isSongInCurrentPlaylist(item.songId) ? '已加入当前歌单' : '加入当前歌单'"
                  @click.stop="addSongToCurrentPlaylist(item)"
                >
                  {{ isSongInCurrentPlaylist(item.songId) ? '✓' : '+' }}
                </button>
              </div>
            </div>
          </div>
          <p v-else class="placeholder-text">没有找到相关结果</p>
        </section>

        <section v-if="searchResults === null && recommendedSongs.length" class="section section-recommend">
          <h2>最近常听</h2>
          <div class="songs-list">
            <div
              v-for="song in recommendedSongs"
              :key="song.id"
              class="song-card"
              @click="goToSong(song.id)"
            >
              <img :src="song.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" />
              <div class="song-info">
                <h3>{{ song.title || '未知歌曲' }}</h3>
                <p>{{ artistNameMap[song.artistId] || (song.artistId ? `歌手 ${song.artistId}` : '未知') }}</p>
              </div>
              <div class="song-card-actions">
                <button
                  class="song-action-btn play-action-btn"
                  title="播放"
                  @click.stop="playRecommendedSong(song)"
                >
                  ▶
                </button>
                <button
                  class="song-action-btn add-action-btn"
                  :class="{ added: isSongInCurrentPlaylist(song.id) }"
                  :title="isSongInCurrentPlaylist(song.id) ? '已加入当前歌单' : '加入当前歌单'"
                  @click.stop="addSongToCurrentPlaylist(song)"
                >
                  {{ isSongInCurrentPlaylist(song.id) ? '✓' : '+' }}
                </button>
              </div>
            </div>
          </div>
        </section>

        <!-- 全部歌曲 -->
        <section class="section section-more">
          <h2>全部歌曲</h2>
          <div class="section-actions">
            <button class="play-all-btn" :disabled="songOperating || !songs.length" @click="playAllSongs">
              {{ songOperating ? '处理中...' : '播放全部' }}
            </button>
            <button class="ranking-link-btn" @click="$router.push('/ranking')">热门排行榜</button>
          </div>
          <div class="songs-list">
            <div
              v-for="song in songs"
              :key="song.id"
              class="song-card"
              @click="goToSong(song.id)"
            >
              <img :src="song.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" />
              <div class="song-info">
                <h3>{{ song.title || '未知歌曲' }}</h3>
                <p>
                  {{
                    artistNameMap[song.artistId] ||
                      (song.artistId ? `歌手 ${song.artistId}` : '未知')
                  }}
                </p>
              </div>
              <div class="song-card-actions">
                <button
                  class="song-action-btn play-action-btn"
                  title="播放"
                  @click.stop="playSongFromList(song)"
                >
                  ▶
                </button>
                <button
                  class="song-action-btn add-action-btn"
                  :class="{ added: isSongInCurrentPlaylist(song.id) }"
                  :title="isSongInCurrentPlaylist(song.id) ? '已加入当前歌单' : '加入当前歌单'"
                  @click.stop="addSongToCurrentPlaylist(song)"
                >
                  {{ isSongInCurrentPlaylist(song.id) ? '✓' : '+' }}
                </button>
              </div>
              <button
                class="favorite-btn"
                :class="{ active: song.favorite }"
                @click.stop="toggleFavorite(song)"
                :title="song.favorite ? '取消收藏' : '收藏歌曲'"
              >
                <span class="heart-icon"></span>
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script>
import musicApi from '@/api/music';
import statisticsApi from '@/api/statistics';
import defaultCover from '@/assets/default-cover.png';
import DOMPurify from 'dompurify';
import StormFrontRain from '@/components/StormFrontRain.vue';

export default {
  name: 'Songs',
  components: {
    StormFrontRain,
  },
  data() {
    return {
      songs: [],
      recommendedSongs: [],
      defaultCover,
      userId: null,
      artistNameMap: {},
      songOperating: false,
      searchKeyword: '',
      searchResults: null,
      searchHistory: [],
      showHistory: false,
      currentPlaylistId: null,
      currentPlaylistSongIds: [],
    };
  },
  created() {
    let userBase = {};
    try { userBase = JSON.parse(localStorage.getItem('userBase') || '{}'); } catch (e) { /* ignore */ }
    this.userId = userBase.id ?? null;
    this.loadSongs();
    this.loadRecommended();
    this.loadSearchHistory();
  },
  mounted() {
    if (this.userId) {
      this.loadCurrentPlaylistState();
    }
    this.$bus.on('refreshCurrentPlaylist', this.handlePlaylistRefresh);
  },
  beforeUnmount() {
    this.$bus.off('refreshCurrentPlaylist', this.handlePlaylistRefresh);
  },
  methods: {
    handlePlaylistRefresh() {
      if (!this.userId) {
        this.currentPlaylistId = null;
        this.currentPlaylistSongIds = [];
        return;
      }
      this.loadCurrentPlaylistState();
    },
    async loadCurrentPlaylistState() {
      if (!this.userId) {
        this.currentPlaylistId = null;
        this.currentPlaylistSongIds = [];
        return;
      }
      try {
        const response = await musicApi.getCurrentPlaylist(this.userId);
        if (response.data?.passed) {
          const playlist = response.data.data || {};
          this.currentPlaylistId = playlist.id || null;
          this.currentPlaylistSongIds = Array.isArray(playlist.songs)
            ? playlist.songs.map(song => Number(song.id)).filter(id => !Number.isNaN(id))
            : [];
        }
      } catch (error) {
        console.error('获取当前播放列表失败', error);
      }
    },
    isSongInCurrentPlaylist(songId) {
      const normalizedId = Number(songId);
      return !!normalizedId && this.currentPlaylistSongIds.includes(normalizedId);
    },
    markSongInCurrentPlaylist(songId) {
      const normalizedId = Number(songId);
      if (!normalizedId || this.currentPlaylistSongIds.includes(normalizedId)) {
        return;
      }
      this.currentPlaylistSongIds = [...this.currentPlaylistSongIds, normalizedId];
    },
    playSongOnly(songId, source) {
      if (!songId) {
        return;
      }
      if (this.userId) {
        this.markSongInCurrentPlaylist(songId);
      }
      this.$bus.emit('playSong', {
        songId,
        source,
      });
    },
    playSearchResult(item) {
      this.playSongOnly(item.songId, 'songs-search');
    },
    playRecommendedSong(song) {
      this.playSongOnly(song.id, 'songs-recommended');
    },
    playSongFromList(song) {
      this.playSongOnly(song.id, 'songs');
    },
    async addSongToCurrentPlaylist(song) {
      const songId = Number(song?.id ?? song?.songId);
      if (!songId) {
        return;
      }
      if (!this.userId) {
        alert('请先登录再加入当前歌单');
        return;
      }
      if (!this.currentPlaylistId) {
        await this.loadCurrentPlaylistState();
      }
      if (!this.currentPlaylistId) {
        alert('获取当前歌单失败，请稍后重试');
        return;
      }
      if (this.isSongInCurrentPlaylist(songId)) {
        return;
      }
      try {
        const response = await musicApi.managePlaylistSong({
          playlistId: this.currentPlaylistId,
          songId,
          action: 'add',
        });
        if (response.data?.passed) {
          this.markSongInCurrentPlaylist(songId);
          this.$bus.emit('refreshCurrentPlaylist');
        } else {
          alert('加入当前歌单失败：' + (response.data?.message || '未知错误'));
        }
      } catch (error) {
        alert('加入当前歌单失败：' + error.message);
      }
    },
    async loadRecommended() {
      if (!this.userId) return;
      try {
        const res = await statisticsApi.getUserTopSongs(this.userId, { limit: 6 });
        if (res.data && res.data.passed && Array.isArray(res.data.data)) {
          const topSongIds = res.data.data.map(item => item.songId).filter(Boolean);
          const songDetails = await Promise.all(
            topSongIds.map(id => musicApi.getSongBaseInfo(id, this.userId).catch(() => null))
          );
          this.recommendedSongs = songDetails
            .filter(r => r && r.data && r.data.passed && r.data.data)
            .map(r => r.data.data);
          const ids = [...new Set(this.recommendedSongs.map(s => s.artistId).filter(Boolean))];
          await Promise.all(ids.map(id => this.ensureArtistName(id)));
        }
      } catch (e) {
        // silent
      }
    },
    async ensureArtistName(id) {
      if (!id || this.artistNameMap[id]) return;
      try {
        const res = await musicApi.getSingerBaseInfo(id);
        if (res.data && res.data.passed && res.data.data) {
          this.$set ? this.$set(this.artistNameMap, id, res.data.data.name) : (this.artistNameMap[id] = res.data.data.name);
        }
      } catch (e) { /* silent */ }
    },
    async loadSongs() {
      try {
        const response = await musicApi.getSongs(this.userId);
        if (response.data && response.data.passed) {
          this.songs = response.data.data || [];
          await this.loadArtistNames();
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('获取歌曲列表失败：' + msg);
        }
      } catch (error) {
        alert('获取歌曲列表失败：' + error.message);
      }
    },
    async loadArtistNames() {
      const ids = Array.from(
        new Set(
          this.songs
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
    async toggleFavorite(song) {
      if (!song || !song.id) {
        return;
      }
      if (!this.userId) {
        alert('请先登录再收藏歌曲');
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
          if (this.userId) {
            statisticsApi.reportEvent({
              userId: this.userId,
              eventType: 'FAVORITE',
              targetType: 'song',
              targetId: song.id,
              extraData: { action: favorite ? 'add' : 'remove' },
            }).catch(() => {});
          }
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('更新收藏状态失败：' + msg);
        }
      } catch (error) {
        alert('更新收藏状态失败：' + error.message);
      }
    },
    async playAllSongs() {
      if (!this.songs.length) {
        alert('当前没有可播放歌曲');
        return;
      }
      const songIds = this.songs
        .map(song => Number(song.id))
        .filter(id => !Number.isNaN(id) && id > 0);
      if (!songIds.length) {
        alert('当前没有可播放歌曲');
        return;
      }
      this.songOperating = true;
      try {
        this.$bus.emit('playSong', {
          songId: songIds[0],
          playlist: songIds,
          index: 0,
          source: 'songs',
        });

        if (this.userId) {
          const response = await musicApi.replaceCurrentPlaylist(this.userId, songIds);
          if (!response.data?.passed) {
            alert('播放队列同步失败：' + (response.data?.message || '未知错误'));
          } else {
            const result = response.data.data || {};
            const synced = Number(result.synced ?? songIds.length);
            const requested = Number(result.requested ?? songIds.length);
            this.currentPlaylistId = result.playlistId || this.currentPlaylistId;
            this.currentPlaylistSongIds = Array.isArray(result.syncedIds) ? result.syncedIds : songIds.slice(0, synced);
            if (synced !== requested) {
              console.warn('播放队列存在未同步歌曲', result.failedIds || []);
              alert(`播放队列同步不完整：成功 ${synced}/${requested} 首`);
            }
          }
          this.$bus.emit('refreshCurrentPlaylist');
        }
      } catch (error) {
        console.error('播放全部服务端同步异常', error);
      } finally {
        this.songOperating = false;
      }
    },
    goToSong(songId) {
      this.$router.push(`/song/${songId}`);
    },
    async loadSearchHistory() {
      if (!this.userId) return;
      try {
        const res = await statisticsApi.getSearchKeywords(this.userId, 8);
        if (res.data?.passed && Array.isArray(res.data.data)) {
          this.searchHistory = res.data.data;
        }
      } catch (e) { /* silent */ }
    },
    useHistoryKeyword(keyword) {
      this.searchKeyword = keyword;
      this.showHistory = false;
      this.handleSearch();
    },
    hideHistoryDelayed() {
      setTimeout(() => { this.showHistory = false; }, 200);
    },
    async handleSearch() {
      this.showHistory = false;
      const kw = this.searchKeyword.trim();
      if (!kw) {
        this.searchResults = null;
        return;
      }
      try {
        const response = await musicApi.search(kw);
        if (response.data && response.data.passed) {
          this.searchResults = response.data.data || [];
          if (this.userId) {
            statisticsApi.reportEvent({
              userId: this.userId,
              eventType: 'SEARCH',
              extraData: { keyword: kw, resultCount: this.searchResults.length },
            }).catch(() => {});
            this.loadSearchHistory();
          }
        } else {
          alert('搜索失败：' + (response.data?.message || '未知错误'));
        }
      } catch (error) {
        alert('搜索出错：' + error.message);
      }
    },
    clearSearch() {
      this.searchKeyword = '';
      this.searchResults = null;
    },
    sanitizeHighlight(html) {
      return DOMPurify.sanitize(html, { ALLOWED_TAGS: ['em'] });
    },
    highlightTitle(item) {
      if (item.highlights && item.highlights.title && item.highlights.title.length) {
        return this.sanitizeHighlight(item.highlights.title[0]);
      }
      return this.sanitizeHighlight(item.title || '未知歌曲');
    },
    highlightSinger(item) {
      if (item.highlights && item.highlights.singerName && item.highlights.singerName.length) {
        return this.sanitizeHighlight(item.highlights.singerName[0]);
      }
      return this.sanitizeHighlight(item.singerName || '未知歌手');
    },
    hasLyricsHighlight(item) {
      if (!item.highlights) return false;
      return (
        (item.highlights.lyricsZh && item.highlights.lyricsZh.length) ||
        (item.highlights.lyricsJa && item.highlights.lyricsJa.length) ||
        (item.highlights.lyricsEn && item.highlights.lyricsEn.length)
      );
    },
    trimAroundHighlight(fragment) {
      const clean = fragment.replace(/\n/g, ' ');
      const emIdx = clean.indexOf('<em>');
      if (emIdx < 0) return clean;
      const pad = 15;
      let start = Math.max(0, emIdx - pad);
      const emEndIdx = clean.lastIndexOf('</em>');
      const afterEm = emEndIdx >= 0 ? emEndIdx + 5 : emIdx + 10;
      let end = Math.min(clean.length, afterEm + pad);
      let trimmed = clean.slice(start, end);
      if (start > 0) trimmed = '...' + trimmed;
      if (end < clean.length) trimmed = trimmed + '...';
      return trimmed;
    },
    getLyricsHighlights(item) {
      if (!item.highlights) return [];
      const all = [];
      for (const key of ['lyricsZh', 'lyricsJa', 'lyricsEn']) {
        if (item.highlights[key]) {
          all.push(...item.highlights[key].map(f => this.sanitizeHighlight(this.trimAroundHighlight(f))));
        }
      }
      return all.slice(0, 3);
    },
  },
};
</script>

<style scoped>
.songs-page {
  position: relative;
  min-height: calc(100vh - 80px);
}

.songs-shell {
  position: relative;
  z-index: 1;
  padding: 20px clamp(96px, 12vw, 180px) 28px;
}

.songs-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  background: linear-gradient(to bottom, #e0f7fa, #ffffff);
  position: relative;
}

@media (max-width: 900px) {
  .songs-shell {
    padding-left: 20px;
    padding-right: 20px;
  }
}

.search-bar {
  max-width: 500px;
  margin: 0 auto 24px;
  position: relative;
}

.search-bar input {
  width: 100%;
  padding: 10px 36px 10px 14px;
  border-radius: 999px;
  border: 1px solid #cbd5e1;
  background-color: #f1f5f9;
  color: #1e293b;
  font-size: 14px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.search-bar input:focus {
  outline: none;
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgba(79, 172, 254, 0.2);
}

.clear-btn {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: none;
  background: #cbd5e1;
  color: #fff;
  font-size: 16px;
  line-height: 24px;
  text-align: center;
  cursor: pointer;
}

.clear-btn:hover {
  background: #94a3b8;
}

.lyrics-snippet {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
}

.lyrics-snippet span {
  display: block;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  word-break: break-all;
}

.lyrics-snippet :deep(em) {
  font-style: normal;
  color: #ef4444;
  font-weight: 600;
}

.song-info :deep(em) {
  font-style: normal;
  color: #ef4444;
  font-weight: 600;
}

.section {
  margin-bottom: 28px;
}

.section h2 {
  margin-bottom: 16px;
  text-align: left;
}

.section-actions {
  margin-bottom: 12px;
}

.play-all-btn {
  border: none;
  border-radius: 8px;
  background: #0ea5e9;
  color: #fff;
  padding: 9px 14px;
  font-size: 14px;
  cursor: pointer;
}

.play-all-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.ranking-link-btn {
  border: 1px solid #f59e0b;
  border-radius: 8px;
  background: transparent;
  color: #f59e0b;
  padding: 9px 14px;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.ranking-link-btn:hover {
  background: #f59e0b;
  color: #fff;
}

.placeholder-text {
  color: #94a3b8;
  font-size: 14px;
}

.songs-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 20px;
}
.song-card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  padding: 15px 15px 58px;
  cursor: pointer;
  transition: transform 0.2s;
  position: relative;
}
.song-card:hover {
  transform: scale(1.05);
}
.song-cover {
  width: 100%;
  height: 150px;
  object-fit: cover;
  border-radius: 8px;
  margin-bottom: 10px;
}
.song-info h3 {
  margin: 0;
  font-size: 18px;
}
.song-info p {
  margin: 5px 0 0;
  color: #666;
  font-size: 14px;
}
.song-card-actions {
  position: absolute;
  right: 12px;
  bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  opacity: 0;
  transform: translateY(4px);
  transition: opacity 0.2s, transform 0.2s;
}
.song-card:hover .song-card-actions {
  opacity: 1;
  transform: translateY(0);
}
.song-action-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.18);
  transition: transform 0.15s, box-shadow 0.15s, opacity 0.15s;
}
.song-action-btn:hover {
  transform: translateY(-1px) scale(1.06);
  box-shadow: 0 10px 18px rgba(15, 23, 42, 0.22);
}
.play-action-btn {
  background: linear-gradient(135deg, #38bdf8, #2563eb);
}
.add-action-btn {
  background: linear-gradient(135deg, #f59e0b, #ea580c);
}
.add-action-btn.added {
  background: linear-gradient(135deg, #34d399, #059669);
}
.favorite-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 40px;
  height: 40px;
  border-radius: 20px;
  border: none;
  background: rgba(255, 255, 255, 0.75);
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
  box-shadow: 0 8px 18px rgba(255, 99, 132, 0.3);
}

@media (hover: none) {
  .song-card-actions {
    opacity: 1;
    transform: translateY(0);
  }
}

.search-history-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 50;
  overflow: hidden;
}

.search-history-header {
  padding: 10px 14px 6px;
  font-size: 12px;
  color: #94a3b8;
  font-weight: 500;
}

.search-history-item {
  padding: 8px 14px;
  font-size: 14px;
  color: #334155;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  transition: background 0.15s;
}

.search-history-item:hover {
  background: #f1f5f9;
}

.search-count {
  font-size: 12px;
  color: #94a3b8;
}
</style>
