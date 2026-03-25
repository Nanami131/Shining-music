<template>
  <div class="ranking-container">
    <h1>热门排行榜</h1>
    <p class="ranking-subtitle">基于全站播放数据实时统计</p>

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
  margin-bottom: 24px;
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
