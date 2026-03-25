<template>
  <div class="history-container">
    <h1>播放历史</h1>
    <div v-if="loading" class="loading-text">加载中...</div>
    <div v-else-if="!userId" class="placeholder-text">请先登录查看播放历史</div>
    <div v-else-if="!records.length" class="placeholder-text">暂无播放记录</div>
    <div v-else class="history-list">
      <div
        v-for="(rec, idx) in records"
        :key="rec.id || idx"
        class="history-item"
        @click="goToSong(rec.songId)"
      >
        <span class="history-index">{{ idx + 1 }}</span>
        <img :src="songInfoMap[rec.songId]?.coverUrl || defaultCover" class="history-cover" alt="" />
        <div class="history-info">
          <h3>{{ songInfoMap[rec.songId]?.title || `歌曲 ${rec.songId}` }}</h3>
          <p>{{ resolveSingerName(rec.songId) }}</p>
        </div>
        <div class="history-meta">
          <span class="history-time">{{ formatTime(rec.playedAt) }}</span>
          <span v-if="rec.durationSec" class="history-duration">{{ formatDuration(rec.durationSec) }}</span>
        </div>
        <button class="play-btn" title="播放" @click.stop="playSong(rec.songId)">&#9654;</button>
      </div>
    </div>
  </div>
</template>

<script>
import statisticsApi from '../api/statistics';
import musicApi from '../api/music';
import defaultCover from '../assets/default-cover.png';

export default {
  name: 'PlayHistory',
  data() {
    return {
      userId: null,
      records: [],
      songInfoMap: {},
      singerMap: {},
      loading: true,
      defaultCover,
    };
  },
  created() {
    let userBase = {};
    try { userBase = JSON.parse(localStorage.getItem('userBase') || '{}'); } catch (e) { /* ignore */ }
    this.userId = userBase.id ?? null;
    this.loadHistory();
  },
  methods: {
    async loadHistory() {
      if (!this.userId) {
        this.loading = false;
        return;
      }
      try {
        const res = await statisticsApi.getRecentPlays(this.userId, 50);
        if (res.data?.passed && Array.isArray(res.data.data)) {
          this.records = res.data.data;
          await this.enrichSongInfo();
        }
      } catch (e) {
        console.error('Failed to load play history', e);
      } finally {
        this.loading = false;
      }
    },
    async enrichSongInfo() {
      const songIds = [...new Set(this.records.map(r => r.songId))];
      const tasks = songIds.map(async (id) => {
        try {
          const res = await musicApi.getSongBaseInfo(id, this.userId);
          if (res.data?.passed && res.data.data) {
            this.songInfoMap[id] = res.data.data;
            const artistId = res.data.data.artistId;
            if (artistId && !this.singerMap[artistId]) {
              const singerRes = await musicApi.getSingerBaseInfo(artistId);
              if (singerRes.data?.passed && singerRes.data.data) {
                this.singerMap[artistId] = singerRes.data.data;
              }
            }
          }
        } catch (e) { /* ignore individual failures */ }
      });
      await Promise.all(tasks);
      this.songInfoMap = { ...this.songInfoMap };
      this.singerMap = { ...this.singerMap };
    },
    resolveSingerName(songId) {
      const song = this.songInfoMap[songId];
      if (!song) return '未知歌手';
      const singer = this.singerMap[song.artistId];
      return singer?.name || (song.artistId ? `歌手 ${song.artistId}` : '未知歌手');
    },
    formatTime(ts) {
      if (!ts) return '';
      const d = new Date(ts);
      if (isNaN(d.getTime())) return ts;
      const now = new Date();
      const isToday = d.toDateString() === now.toDateString();
      const hhmm = d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
      if (isToday) return `今天 ${hhmm}`;
      const yesterday = new Date(now);
      yesterday.setDate(yesterday.getDate() - 1);
      if (d.toDateString() === yesterday.toDateString()) return `昨天 ${hhmm}`;
      return `${d.getMonth() + 1}/${d.getDate()} ${hhmm}`;
    },
    formatDuration(sec) {
      if (!sec || sec <= 0) return '';
      const m = Math.floor(sec / 60);
      const s = Math.floor(sec % 60);
      return `${m}:${s.toString().padStart(2, '0')}`;
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
.history-container {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
  background: linear-gradient(to bottom, #e0f7fa, #ffffff);
  min-height: calc(100vh - 120px);
}

.history-container h1 {
  font-size: 24px;
  margin-bottom: 20px;
  color: #1e293b;
}

.loading-text,
.placeholder-text {
  color: #94a3b8;
  font-size: 14px;
  text-align: center;
  padding: 40px 0;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  transition: background 0.2s, transform 0.15s;
}

.history-item:hover {
  background: rgba(255, 255, 255, 1);
  transform: translateX(4px);
}

.history-index {
  width: 28px;
  text-align: center;
  color: #94a3b8;
  font-size: 14px;
  font-weight: 500;
  flex-shrink: 0;
}

.history-cover {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  object-fit: cover;
  flex-shrink: 0;
}

.history-info {
  flex: 1;
  min-width: 0;
}

.history-info h3 {
  margin: 0;
  font-size: 15px;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-info p {
  margin: 2px 0 0;
  font-size: 13px;
  color: #64748b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex-shrink: 0;
}

.history-time {
  font-size: 12px;
  color: #94a3b8;
}

.history-duration {
  font-size: 12px;
  color: #64748b;
}

.play-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: #0ea5e9;
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
  background: #0284c7;
  transform: scale(1.1);
}
</style>
