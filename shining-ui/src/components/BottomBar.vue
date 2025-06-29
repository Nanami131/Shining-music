<template>
  <div class="bottom-bar">
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
      <div class="highlight-color-selector">
        <select v-model="highlightColor" @change="updateHighlightColor">
          <option value="pink">粉色</option>
          <option value="blue">蓝色</option>
          <option value="green">绿色</option>
          <option value="purple">紫色</option>
        </select>
      </div>
    </div>

    <div class="lyrics-panel" v-if="showLyrics">
      <div class="playlist-placeholder"></div>
      <div class="lyrics-content" ref="lyricsContent" :class="'highlight-color-' + highlightColor">
        <div v-for="(line, index) in parsedLyrics" :key="index" class="lyrics-group">
          <div
              :class="{ active: isActiveLine(line.time, index) }"
              class="lyric-line"
              ref="lyricLines"
          >
            <p v-if="line.ja">{{ line.ja }}</p>
            <p v-if="line.zh">{{ line.zh }}</p>
          </div>
          <p v-if="!line.zh && !line.ja">无歌词内容</p>
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
  name: 'BottomBar',
  data() {
    return {
      currentSong: {},
      currentLyrics: {},
      parsedLyrics: [], // [{ time, zh: text, ja: text }]
      audio: new Audio(),
      isPlaying: false,
      currentTime: 0,
      duration: 0,
      showLyrics: false,
      defaultCover,
      highlightColor: 'pink', // 默认高亮颜色
    };
  },
  created() {
    this.audio.addEventListener('timeupdate', this.updateProgress);
    this.audio.addEventListener('loadedmetadata', this.updateDuration);
    this.audio.addEventListener('ended', this.handleEnded);
    this.$bus.on('playSong', this.playSong);
  },
  beforeDestroy() {
    this.audio.removeEventListener('timeupdate', this.updateProgress);
    this.audio.removeEventListener('loadedmetadata', this.updateDuration);
    this.audio.removeEventListener('ended', this.handleEnded);
    this.$bus.off('playSong', this.playSong);
    this.audio.pause();
    this.audio.src = '';
  },
  methods: {
    async playSong({ songId }) {
      try {
        this.audio.pause();
        this.audio.src = '';
        this.currentTime = 0;
        this.isPlaying = false;

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
          alert('无歌词数据');
        }
      } catch (error) {
        this.currentLyrics = {};
        this.parsedLyrics = [];
        alert('加载歌词出错：' + error.message);
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
    isActiveLine(time, index) {
      const isActive =
          this.currentTime >= time &&
          (index + 1 >= this.parsedLyrics.length || this.currentTime < this.parsedLyrics[index + 1].time);
      if (isActive && this.showLyrics) {
        this.$nextTick(() => this.scrollToActiveLine(index));
      }
      return isActive;
    },
    scrollToActiveLine(index) {
      const lyricsContent = this.$refs.lyricsContent;
      const activeLine = this.$refs.lyricLines[index];
      if (lyricsContent && activeLine) {
        lyricsContent.scrollTo({
          top: activeLine.offsetTop - lyricsContent.offsetTop - 50,
          behavior: 'smooth',
        });
      }
    },
    togglePlay() {
      if (this.isPlaying) {
        this.audio.pause();
        this.isPlaying = false;
      } else if (this.audio.src) {
        this.audio.play();
        this.isPlaying = true;
      }
    },
    updateProgress() {
      this.currentTime = this.audio.currentTime;
    },
    updateDuration() {
      this.duration = this.audio.duration || 0;
    },
    seek() {
      if (this.currentSong.fileUrl) {
        this.audio.pause();
        this.audio.src = this.currentSong.fileUrl;
        this.audio.currentTime = this.currentTime;
        if (this.currentTime < this.duration && this.isPlaying) {
          this.audio.play();
        }
      }
    },
    handleEnded() {
      this.currentTime = 0;
      this.isPlaying = false;
      this.audio.currentTime = 0;
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
    updateHighlightColor() {
      // 颜色切换通过动态class绑定完成，无需额外处理
    },
  },
};
</script>

<style scoped>

.lyric-panel {
  width: 100%;
  height: 100%;
  overflow: hidden;
  position: relative;
  background: rgba(255, 255, 255, 0.1);
  padding: 0 25px;
}

.lyric-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 0;
}
.lyric-header .title {
  font-size: 20px;
  color: #2c3e50;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 1px;
}
.lyric-header .controls {
  display: flex;
  gap: 20px;
}
.lyric-header .lang-select,
.lyric-header .color-select {
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
  background: var(--highlight-color);
  color: #fff;
  box-shadow: 0 0 8px rgba(0, 0, 0, 0.2);
  transform: scale(1.1);
}
.color-btn {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 2px solid #fff;
}
.color-btn.pink {
  background: #ff6b81;
}
.color-btn.blue {
  background: #3498db;
}
.color-btn.purple {
  background: #9b59b6;
}
.color-btn.green {
  background: #2ecc71;
}
.color-btn:hover {
  transform: scale(1.2);
}
.color-btn.active {
  box-shadow: 0 0 6px rgba(0, 0, 0, 0.3);
  transform: scale(1.2);
}

.lyric-wrapper {
  position: relative;
  height: calc(100% - 60px);
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.has-lyric {
  width: 100%;
  padding: 20px 0;
}
.has-lyric li {
  width: 100%;
  height: 40px;
  text-align: center;
  font-size: 14px;
  line-height: 40px;
  color: #555;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}
.has-lyric li.active {
  color: var(--highlight-color);
  font-size: 18px;
  background: var(--highlight-bg);
}

.no-lyric {
  text-align: center;
  padding-top: 20px;
}
.no-lyric span {
  font-size: 18px;
  color: #95a5a6;
  font-style: italic;
}

/* 动态高亮颜色 */
.lyrics-content.highlight-color-pink .lyric-line.active {
  --highlight-color: #ff6b81;
  --highlight-bg: rgba(255, 107, 129, 0.2);
}
.lyrics-content.highlight-color-blue .lyric-line.active {
  --highlight-color: #3498db;
  --highlight-bg: rgba(52, 152, 219, 0.2);
}
.lyrics-content.highlight-color-green .lyric-line.active {
  --highlight-color: #2ecc71;
  --highlight-bg: rgba(46, 204, 113, 0.2);
}
.lyrics-content.highlight-color-purple .lyric-line.active {
  --highlight-color: #9b59b6;
  --highlight-bg: rgba(155, 89, 182, 0.2);
}

/* 以下为底部栏样式 */
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 50px;
  background: #fff;
  box-shadow: 0 -2px 4px rgba(0, 0, 0, 0.1);
  z-index: 1000;
}
.fixed-bar {
  display: flex;
  align-items: center;
  padding: 10px 20px;
  gap: 20px;
  height: 100%;
}
.song-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
}
.song-cover {
  width: 40px;
  height: 40px;
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
  gap: 8px;
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
  height: 4px;
  border-radius: 2px;
  background: #dfe6e9;
  appearance: none;
  cursor: pointer;
}
.progress-slider::-webkit-slider-thumb {
  appearance: none;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #ff6b81;
  box-shadow: 0 0 6px rgba(0, 0, 0, 0.2);
}
.control-buttons {
  display: flex;
  gap: 10px;
  justify-content: center;
}
.control-buttons button {
  padding: 6px 12px;
  border: none;
  border-radius: 4px;
  background: linear-gradient(to right, #4facfe, #00f2fe);
  color: white;
  font-size: 14px;
  cursor: pointer;
}
.control-buttons button:disabled {
  background: #ccc;
  cursor: not-allowed;
}
.toggle-lyrics {
  padding: 6px 12px;
  border: none;
  border-radius: 4px;
  background: linear-gradient(to right, #4facfe, #00f2fe);
  color: white;
  font-size: 14px;
  cursor: pointer;
}
.lyrics-panel {
  position: fixed;
  bottom: 50px;
  left: 0;
  right: 0;
  max-height: 200px;
  display: flex;
  width: 100%;
  z-index: 999;
}
.playlist-placeholder {
  width: 50%;
  background: #f5f5f5;
}
.lyrics-content {
  width: 50%;
  padding: 20px;
  overflow-y: auto;
  background: #fafafa;
}
.lyrics-group {
  margin-bottom: 20px;
}
.lyric-line {
  font-size: 16px;
  color: #333;
  margin-bottom: 8px;
  font-family: 'KaiTi', 'STKaiti', '楷体', sans-serif;
}
.lyric-line.active {
  color: var(--highlight-color);
  background: var(--highlight-bg);
  font-weight: 600;
}
.lyric-line p {
  margin: 2px 0;
}
.lyrics-content p:not(.lyric-line p) {
  font-size: 16px;
  color: #666;
  font-family: 'KaiTi', 'STKaiti', '楷体', sans-serif;
}
.highlight-color-selector {
  display: inline-block;
}
.highlight-color-selector select {
  padding: 4px;
  border-radius: 4px;
  border: 1px solid #ccc;
  font-size: 14px;
  cursor: pointer;
}
</style>
