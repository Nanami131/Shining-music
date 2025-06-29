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
    <h3>歌词</h3>
    <div class="lyrics-panel">
      <div class="lyric-header">
        <div class="controls">
          <div class="lyrics-select">
            <select v-model="selectedLyricId" @change="loadSelectedLyrics">
              <option v-for="lyric in allLyrics" :key="lyric.id" :value="lyric.id">
                歌词版本 {{ lyric.id }}
              </option>
            </select>
          </div>
          <div class="lang-select">
            <span class="lang-btn" :class="{ active: selectedLang === 'zh' }" @click="setLanguage('zh')">中</span>
            <span class="lang-btn" :class="{ active: selectedLang === 'ja' }" @click="setLanguage('ja')">日</span>
            <span class="lang-btn" :class="{ active: selectedLang === 'all' }" @click="setLanguage('all')">全</span>
          </div>
        </div>
      </div>
      <div class="lyrics-content">
        <div v-for="(line, index) in parsedLyrics" :key="index" class="lyrics-group">
          <div class="lyric-line">
            <template v-if="selectedLang === 'all'">
              <p v-if="line.zh">{{ line.zh }}</p>
              <p v-if="line.ja">{{ line.ja }}</p>
              <p v-if="!line.zh && !line.ja">无歌词内容</p>
            </template>
            <template v-else>
              <p v-if="line[selectedLang]">{{ line[selectedLang] }}</p>
              <p v-else>无歌词内容</p>
            </template>
          </div>
        </div>
        <p v-if="!parsedLyrics.length" class="no-lyric"><span>暂无歌词</span></p>
      </div>
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
      allLyrics: [],
      selectedLyricId: null,
      selectedLang: 'zh',
      parsedLyrics: [],
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
          await this.loadAllLyrics(songId);
        } else {
          alert('获取歌曲详情失败：' + response.data.message);
        }
      } catch (error) {
        alert('获取歌曲详情出错：' + error.message);
      }
    },
    async loadAllLyrics(songId) {
      try {
        const response = await musicApi.getAllLyrics(songId);
        if (response.data.passed && response.data.data.length > 0) {
          this.allLyrics = response.data.data;
          this.selectedLyricId = this.allLyrics[0].id;
          this.loadSelectedLyrics();
        } else {
          this.allLyrics = [];
          this.selectedLyricId = null;
          this.parsedLyrics = [];
          alert('无歌词数据');
        }
      } catch (error) {
        this.allLyrics = [];
        this.selectedLyricId = null;
        this.parsedLyrics = [];
        alert('加载歌词出错：' + error.message);
      }
    },
    loadSelectedLyrics() {
      const selectedLyric = this.allLyrics.find(lyric => lyric.id === this.selectedLyricId);
      if (selectedLyric) {
        this.parseLyrics(selectedLyric.content || '');
      } else {
        this.parsedLyrics = [];
      }
    },
    parseLyrics(content) {
      this.parsedLyrics = [];
      if (!content) return;
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
      this.parsedLyrics = Object.values(timeMap).sort((a, b) => a.time - b.time);
    },
    timeToSeconds(timeStr) {
      const [min, sec] = timeStr.split(':').map(parseFloat);
      return min * 60 + sec;
    },
    playSong() {
      this.$bus.emit('playSong', { songId: this.song.id });
    },
    setLanguage(lang) {
      this.selectedLang = lang;
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
.lyrics-panel {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  padding: 15px;
}
.lyric-header {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 10px 0;
}
.lyric-header .controls {
  display: flex;
  gap: 20px;
  align-items: center;
}
.lyrics-select select {
  padding: 4px;
  border-radius: 4px;
  border: 1px solid #ccc;
  font-size: 14px;
  cursor: pointer;
}
.lang-select {
  display: flex;
  gap: 10px;
}
.lang-btn {
  width: 36px;
  height: 36px;
  line-height: 36px;
  text-align: center;
  border-radius: 50%;
  background: #dfe6e9;
  color: #636e72;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
}
.lang-btn:hover {
  background: #b2bec3;
  color: #fff;
}
.lang-btn.active {
  background: #4facfe;
  color: #fff;
  box-shadow: 0 0 8px rgba(0, 0, 0, 0.2);
  transform: scale(1.1);
}
.lyrics-content {
  max-height: 400px;
  padding: 20px;
  overflow-y: auto;
  background: #edf1f6;
  text-align: center;
}
.lyrics-group {
  margin-bottom: 20px;
}
.lyric-line {
  font-size: 16px;
  color: #333;
  margin-bottom: 8px;
  font-family: 'KaiTi', 'STKaiti', '楷体', sans-serif;
  display: inline-block;
  width: 100%;
  padding: 8px 0;
}
.lyric-line p {
  margin: 2px 0;
}
.lyrics-content p:not(.lyric-line p) {
  font-size: 16px;
  color: #666;
  font-family: 'KaiTi', 'STKaiti', '楷体', sans-serif;
}
.no-lyric {
  text-align: center;
  font-size: 16px;
  color: #666;
}
</style>