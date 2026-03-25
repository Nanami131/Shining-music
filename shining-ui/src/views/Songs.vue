<template>
  <div class="songs-container">
    <div class="search-bar">
      <input
        v-model="searchKeyword"
        type="text"
        placeholder="搜索歌曲名、歌手、歌词..."
        @keyup.enter="handleSearch"
      />
      <button v-if="searchKeyword" class="clear-btn" @click="clearSearch">×</button>
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
</template>

<script>
import musicApi from '@/api/music';
import statisticsApi from '@/api/statistics';
import defaultCover from '@/assets/default-cover.png';
import DOMPurify from 'dompurify';

export default {
  name: 'Songs',
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
    };
  },
  created() {
    const userBase = JSON.parse(localStorage.getItem('userBase') || '{}');
    this.userId = userBase.id ?? null;
    this.loadSongs();
    this.loadRecommended();
  },
  methods: {
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
      this.songOperating = true;
      try {
        this.$bus.emit('playSong', {
          songId: this.songs[0].id,
          playlist: this.songs.map(song => song.id),
          index: 0,
          source: 'songs',
        });

        if (this.userId) {
          await musicApi.clearCurrentPlaylist(this.userId).catch(() => {});
          const currentResponse = await musicApi.getCurrentPlaylist(this.userId);
          const currentPlaylistId = currentResponse.data?.data?.id;
          if (currentPlaylistId) {
            for (const song of this.songs) {
              await musicApi.managePlaylistSong({
                playlistId: currentPlaylistId,
                songId: song.id,
                action: 'add',
              }).catch(() => {});
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
    async handleSearch() {
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
.songs-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  background: linear-gradient(to bottom, #e0f7fa, #ffffff);
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
  padding: 15px;
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
</style>
