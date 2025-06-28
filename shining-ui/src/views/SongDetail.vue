<template>
  <div class="song-detail-container">
    <h2>{{ song.title || '未知歌曲' }}</h2>
    <div class="song-content">
      <img :src="song.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" />
      <div class="song-info">
        <p><strong>歌手 ID：</strong>{{ song.artistId || '未知' }}</p>
        <p><strong>专辑 ID：</strong>{{ song.albumId || '未知' }}</p>
        <p><strong>状态：</strong>{{ song.status || '未知' }}</p>
        <p><strong>创建时间：</strong>{{ song.createdAt || '未知' }}</p>
        <p><strong>更新时间：</strong>{{ song.updatedAt || '未知' }}</p>
        <button class="play-btn" @click="playSong">播放</button>
      </div>
    </div>
    <h3>歌词列表</h3>
    <div class="lyrics-list">
      <div v-for="lyric in song.allLyrics" :key="lyric.id" class="lyric-card">
        <p><strong>语言：</strong>{{ lyric.languageMsg || '未知' }}</p>
        <div v-for="(line, index) in parseLyrics(lyric.content)" :key="index">
          <div class="lyric-line">
            <p v-if="line.zh">{{ line.zh }}</p>
            <p v-if="line.ja">{{ line.ja }}</p>
          </div>
          <p v-if="!line.zh && !line.ja">无歌词内容</p>
        </div>
        <p v-if="!lyric.content">无歌词</p>
        <p><strong>创建时间：</strong>{{ lyric.createdAt || '未知' }}</p>
        <p><strong>更新时间：</strong>{{ lyric.updatedAt || '未知' }}</p>
      </div>
      <p v-if="!song.allLyrics || song.allLyrics.length === 0">无歌词</p>
    </div>
  </div>
</template>

<script>
import musicApi from '@/api/music';
import defaultCover from '@/assets/default-cover.png';

export default {
  name: 'SongDetail',
  data() {
    return {
      song: {},
      defaultCover,
    };
  },
  created() {
    this.loadSongDetails();
  },
  methods: {
    async loadSongDetails() {
      try {
        const songId = this.$route.params.id;
        const response = await musicApi.getSongDetailsInfo(songId);
        if (response.data.passed) {
          this.song = response.data.data;
        } else {
          alert('获取歌曲详情失败：' + response.data.message);
        }
      } catch (error) {
        alert('获取歌曲详情出错：' + error.message);
      }
    },
    playSong() {
      this.$bus.emit('playSong', { songId: this.song.id });
    },
    parseLyrics(content) {
      const parsed = [];
      if (!content) return parsed;
      const lines = content.split('\n').map(line => line.trim());
      const timeMap = {};
      lines.forEach(line => {
        const langMatch = line.match(/^\[(\d+:\d+\.\d+)\]\[(\w+)\](.+)$/);
        if (langMatch) {
          const [, time, lang, text] = langMatch;
          const parsedTime = this.timeToSeconds(time);
          if (!timeMap[parsedTime]) {
            timeMap[parsedTime] = { time: parsedTime };
          }
          timeMap[parsedTime][lang] = text.trim();
        }
      });
      return Object.values(timeMap).sort((a, b) => a.time - b.time);
    },
    timeToSeconds(timeStr) {
      const [min, sec] = timeStr.split(':').map(parseFloat);
      return min * 60 + sec;
    },
  },
};
</script>

<style scoped>
.song-detail-container {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
  background: linear-gradient(to bottom, #e0f7fa, #ffffff);
}
h2 {
  text-align: center;
  margin-bottom: 20px;
}
.song-content {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
}
.song-cover {
  width: 200px;
  height: 200px;
  object-fit: cover;
  border-radius: 8px;
}
.song-info p {
  margin: 10px 0;
  font-size: 16px;
}
.play-btn {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  background: linear-gradient(to right, #4facfe, #00f2fe);
  color: white;
  cursor: pointer;
}
.play-btn:hover {
  opacity: 0.9;
}
h3 {
  margin: 20px 0 10px;
}
.lyrics-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.lyric-card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  padding: 15px;
}
.lyric-card p {
  margin: 0 0 10px;
  font-size: 16px;
}
.lyric-line {
  font-size: 14px;
  color: #333;
  margin-bottom: 8px;
}
.lyric-line p {
  margin: 2px 0;
}
</style>