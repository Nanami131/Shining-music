<template>
  <div class="ranking-container">
    <h1>热门排行榜</h1>
    <p class="ranking-subtitle">基于全站播放数据实时统计</p>

    <button
      class="play-all-btn"
      :disabled="songOperating || loading || !songs.length"
      @click="playAllSongs"
    >
      {{ songOperating ? '处理中...' : '播放全部' }}
    </button>

    <div v-if="loading" class="loading-text">加载中...</div>
    <div v-else-if="!songs.length" class="placeholder-text">暂无排行数据</div>
    <div v-else class="ranking-list">
      <div
        v-for="(item, idx) in songs"
        :key="item.songId"
        class="ranking-item"
        :class="{ 'top-three': idx < 3 }"
        @click="goToSong(item.songId)"
      >
        <span class="rank-number" :class="'rank-' + (idx < 3 ? idx + 1 : 'other')">{{ idx + 1 }}</span>
        <img :src="item.coverUrl || defaultCover" class="rank-cover" alt="" />
        <div class="rank-info">
          <h3>{{ item.title || `歌曲 ${item.songId}` }}</h3>
          <p>{{ item.singerName || '未知歌手' }}</p>
        </div>
        <div class="rank-stats">
          <span class="play-count">{{ item.playCount }} 次播放</span>
        </div>
        <button class="play-btn" title="播放" @click.stop="playSong(item.songId)">&#9654;</button>
      </div>
    </div>
  </div>
</template>

<script>
import statisticsApi from '../api/statistics';
import musicApi from '../api/music';
import defaultCover from '../assets/default-cover.png';

export default {
  name: 'Ranking',
  data() {
    return {
      songs: [],
      loading: true,
      defaultCover,
      userId: null,
      songOperating: false,
    };
  },
  created() {
    let userBase = {};
    try { userBase = JSON.parse(localStorage.getItem('userBase') || '{}'); } catch (e) { /* ignore */ }
    this.userId = userBase.id ?? null;
    this.loadRanking();
  },
  methods: {
    async loadRanking() {
      try {
        const res = await statisticsApi.getGlobalTopSongs(30);
        if (res.data?.passed && Array.isArray(res.data.data)) {
          const raw = res.data.data;
          const enriched = await Promise.all(
            raw.map(async (item) => {
              const out = { songId: item.songId, playCount: item.playCount };
              try {
                const songRes = await musicApi.getSongBaseInfo(item.songId, this.userId);
                if (songRes.data?.passed && songRes.data.data) {
                  const s = songRes.data.data;
                  out.title = s.title;
                  out.coverUrl = s.coverUrl;
                  out.randomEnabled = s.randomEnabled;
                  if (s.artistId) {
                    const singerRes = await musicApi.getSingerBaseInfo(s.artistId);
                    if (singerRes.data?.passed && singerRes.data.data) {
                      out.singerName = singerRes.data.data.name;
                    }
                  }
                }
              } catch (e) { /* ignore */ }
              return out;
            })
          );
          this.songs = enriched;
        }
      } catch (e) {
        console.error('Failed to load ranking', e);
      } finally {
        this.loading = false;
      }
    },
    goToSong(songId) {
      this.$router.push(`/song/${songId}`);
    },
    playSong(songId) {
      this.$bus.emit('playSong', { songId, userId: this.userId });
    },
    isRandomEnabled(song) {
      return song?.randomEnabled !== 0 && song?.randomEnabled !== false;
    },
    async playAllSongs() {
      const songIds = this.songs
        .filter(song => this.isRandomEnabled(song))
        .map(song => Number(song.songId))
        .filter(id => !Number.isNaN(id) && id > 0);
      if (!songIds.length) {
        alert('当前没有可播放歌曲');
        return;
      }

      this.songOperating = true;
      let serverSynced = false;
      if (this.userId) {
        try {
          const response = await musicApi.replaceCurrentPlaylist(this.userId, songIds);
          if (!response.data?.passed) {
            console.warn('播放队列同步失败', response.data?.message);
          } else {
            const result = response.data.data || {};
            const synced = Number(result.synced ?? songIds.length);
            const requested = Number(result.requested ?? songIds.length);
            serverSynced = true;
            if (synced !== requested) {
              console.warn('播放队列存在未同步歌曲', result.failedIds || []);
            }
          }
        } catch (error) {
          console.error('播放全部服务端同步异常', error);
        }
        if (serverSynced) {
          this.$bus.emit('refreshCurrentPlaylist');
        }
      }

      this.$bus.emit('playSong', {
        songId: songIds[0],
        playlist: songIds,
        index: 0,
        source: 'ranking',
        skipServerSync: serverSynced,
      });
      this.songOperating = false;
    },
  },
};
</script>

<style scoped>
.ranking-container {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
  background: linear-gradient(to bottom, #fef3c7, #ffffff);
  min-height: calc(100vh - 120px);
}

.ranking-container h1 {
  font-size: 26px;
  color: #1e293b;
  margin-bottom: 4px;
}

.ranking-subtitle {
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 12px;
}

.play-all-btn {
  border: none;
  border-radius: 8px;
  background: #0ea5e9;
  color: #fff;
  padding: 9px 14px;
  margin-bottom: 12px;
  font-size: 14px;
  cursor: pointer;
}

.play-all-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading-text,
.placeholder-text {
  color: #94a3b8;
  font-size: 14px;
  text-align: center;
  padding: 40px 0;
}

.ranking-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ranking-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.75);
  cursor: pointer;
  transition: background 0.2s, transform 0.15s;
}

.ranking-item:hover {
  background: rgba(255, 255, 255, 1);
  transform: translateX(4px);
}

.ranking-item.top-three {
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.rank-number {
  width: 32px;
  text-align: center;
  font-size: 18px;
  font-weight: 700;
  flex-shrink: 0;
  color: #94a3b8;
}

.rank-1 { color: #f59e0b; }
.rank-2 { color: #8b5cf6; }
.rank-3 { color: #f97316; }

.rank-cover {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}

.rank-info {
  flex: 1;
  min-width: 0;
}

.rank-info h3 {
  margin: 0;
  font-size: 15px;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rank-info p {
  margin: 2px 0 0;
  font-size: 13px;
  color: #64748b;
}

.rank-stats {
  flex-shrink: 0;
}

.play-count {
  font-size: 13px;
  color: #f59e0b;
  font-weight: 500;
}

.play-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: #f59e0b;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.2s, transform 0.15s;
}

.play-btn:hover {
  background: #d97706;
  transform: scale(1.1);
}
</style>
