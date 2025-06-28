<template>
  <div class="bottom-bar">
    <!-- 固定部分：歌曲信息、进度、控制 -->
    <div class="fixed-bar">
      <div class="song-info">
        <img :src="currentSong.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" />
        <div class="song-details">
          <span>{{ currentSong.title || '未知歌曲' }}</span>
          <span class="artist">歌手 ID: {{ currentSong.artistId || '未知' }}</span>
        </div>
      </div>
      <div class="player-controls">
        <div class="progress-bar">
          <span>{{ formatTime(currentTime) }}</span>
          <input
              type="range"
              v-model="currentTime"
              :max="duration"
              @input="seek"
              class="progress-slider"
          />
          <span>{{ formatTime(duration) }}</span>
        </div>
        <div class="control-buttons">
          <button disabled>上一曲</button>
          <button @click="togglePlay">{{ isPlaying ? '暂停' : '播放' }}</button>
          <button disabled>下一曲</button>
        </div>
      </div>
      <button class="toggle-lyrics" @click="toggleLyrics">
        {{ showLyrics ? '收起歌词' : '展开歌词' }}
      </button>
    </div>

    <!-- 可展开部分：歌词 -->
    <div class="lyrics-panel" v-if="showLyrics">
      <h3>歌词</h3>
      <div class="lyrics-content">
        <div
            v-for="(line, index) in parsedLyrics"
            :key="index"
            :class="{ active: isActiveLine(line.time) }"
            class="lyric-line"
        >
          <span v-for="(text, lang) in line.text" :key="lang">[{{ lang }}] {{ text }}</span>
        </div>
        <p v-if="!parsedLyrics.length">无歌词</p>
      </div>
    </div>
  </div>
</template>

<script>
import musicApi from '@/api/music';
import defaultCover from '@/assets/default-cover.png';

export default {
  name: 'BottomBar',
  data() {
    return {
      currentSong: {},
      currentLyrics: {},
      parsedLyrics: [], // 解析后的歌词：[{ time: 秒, text: { ja: '', zh: '' } }]
      audio: new Audio(),
      isPlaying: false,
      currentTime: 0,
      duration: 0,
      showLyrics: false,
      defaultCover,
    };
  },
  created() {
    this.audio.addEventListener('timeupdate', this.updateProgress);
    this.audio.addEventListener('loadedmetadata', this.updateDuration);
    this.$bus.on('playSong', this.playSong);
  },
  beforeDestroy() {
    this.audio.removeEventListener('timeupdate', this.updateProgress);
    this.audio.removeEventListener('loadedmetadata', this.updateDuration);
    this.$bus.off('playSong', this.playSong);
  },
  methods: {
    async playSong({ songId }) {
      try {
        const response = await musicApi.getSongDetailsInfo(songId);
        if (response.data.passed) {
          this.currentSong = response.data.data;
          this.audio.src = this.currentSong.fileUrl || '';
          this.audio.play();
          this.isPlaying = true;
          this.loadLyrics(songId);
        } else {
          alert('获取歌曲详情失败：' + response.data.message);
        }
      } catch (error) {
        alert('播放歌曲出错：' + error.message);
      }
    },
    async loadLyrics(songId) {
      try {
        const response = await musicApi.getAllLyrics(songId);
        if (response.data.passed && response.data.data.length > 0) {
          this.currentLyrics = response.data.data[0];
          this.parseLyrics(this.currentLyrics.content || '');
        } else {
          this.currentLyrics = {};
          this.parsedLyrics = [];
        }
      } catch (error) {
        this.currentLyrics = {};
        this.parsedLyrics = [];
      }
    },
    parseLyrics(content) {
      this.parsedLyrics = [];
      const lines = content.split('\n');
      let currentTime = null;
      let currentText = {};
      lines.forEach(line => {
        const timeMatch = line.match(/\[(\d+:\d+\.\d+)\]/);
        const langMatch = line.match(/\[(\w+)\](.+)/);
        if (timeMatch) {
          currentTime = this.timeToSeconds(timeMatch[1]);
          if (Object.keys(currentText).length) {
            this.parsedLyrics.push({ time: currentTime, text: currentText });
            currentText = {};
          }
        } else if (langMatch && currentTime !== null) {
          const [, lang, text] = langMatch;
          currentText[lang] = text.trim();
        }
      });
      if (currentTime !== null && Object.keys(currentText).length) {
        this.parsedLyrics.push({ time: currentTime, text: currentText });
      }
    },
    timeToSeconds(timeStr) {
      const [min, sec] = timeStr.split(':').map(parseFloat);
      return min * 60 + sec;
    },
    isActiveLine(time) {
      return this.currentTime >= time && (!this.parsedLyrics[this.parsedLyrics.findIndex(l => l.time === time) + 1] || this.currentTime < this.parsedLyrics[this.parsedLyrics.findIndex(l => l.time === time) + 1].time);
    },
    togglePlay() {
      if (this.isPlaying) {
        this.audio.pause();
      } else {
        this.audio.play();
      }
      this.isPlaying = !this.isPlaying;
    },
    updateProgress() {
      this.currentTime = this.audio.currentTime;
    },
    updateDuration() {
      this.duration = this.audio.duration || 0;
    },
    seek() {
      this.audio.currentTime = this.currentTime;
    },
    formatTime(seconds) {
      if (!seconds) return '00:00';
      const min = Math.floor(seconds / 60);
      const sec = Math.floor(seconds % 60);
      return `${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}`;
    },
    toggleLyrics() {
      this.showLyrics = !this.showLyrics;
    },
  },
};
</script>

<style scoped>
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: white;
  box-shadow: 0 -2px 4px rgba(0, 0, 0, 0.1);
  z-index: 1000;
}
.fixed-bar {
  display: flex;
  align-items: center;
  padding: 10px 20px;
  gap: 20px;
}
.song-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
}
.song-cover {
  width: 50px;
  height: 50px;
  object-fit: cover;
  border-radius: 4px;
}
.song-details {
  display: flex;
  flex-direction: column;
}
.song-details span {
  font-size: 14px;
}
.song-details .artist {
  font-size: 12px;
  color: #666;
}
.player-controls {
  flex: 2;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.progress-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}
.progress-bar span {
  font-size: 12px;
  color: #666;
}
.progress-slider {
  flex: 1;
}
.control-buttons {
  display: flex;
  gap: 10px;
  justify-content: center;
}
.control-buttons button {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  background: linear-gradient(to right, #4facfe, #00f2fe);
  color: white;
  cursor: pointer;
}
.control-buttons button:disabled {
  background: #ccc;
  cursor: not-allowed;
}
.toggle-lyrics {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  background: linear-gradient(to right, #4facfe, #00f2fe);
  color: white;
  cursor: pointer;
}
.lyrics-panel {
  background: white;
  padding: 20px;
  max-height: 300px;
  overflow-y: auto;
  border-top: 1px solid #ddd;
}
.lyrics-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.lyric-line {
  font-size: 14px;
  color: #333;
}
.lyric-line.active {
  color: #4facfe;
  font-weight: bold;
}
.lyric-line span {
  display: block;
}
</style>