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
      <!-- 颜色选择按钮移到固定栏 -->
      <div class="highlight-color-selector">
        <select v-model="highlightColor" @change="updateHighlightColor">
          <option value="pink" style="background-color: #ff6b81; color: white;">粉色</option>
          <option value="blue" style="background-color: #4facfe; color: white;">蓝色</option>
          <option value="green" style="background-color: #00cc00; color: white;">绿色</option>
          <option value="purple" style="background-color: #9933ff; color: white;">紫色</option>
        </select>
      </div>
    </div>

    <!-- 歌词面板：当前页面覆盖 -->
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
        // 清理音频
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
          top: activeLine.offsetTop - lyricsContent.offsetTop - 50, // 居中偏移
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
      // 动态类绑定已处理颜色切换
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
  font-size: 16px;
  color: var(--highlight-color);
  background: var(--highlight-bg);
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

/* 动态高亮颜色 */
.lyrics-content.highlight-color-pink .lyric-line.active {
  --highlight-color: #ff6b81;
  --highlight-bg: rgba(255, 107, 129, 0.2);
}
.lyrics-content.highlight-color-blue .lyric-line.active {
  --highlight-color: #4facfe;
  --highlight-bg: rgba(79, 172, 254, 0.2);
}
.lyrics-content.highlight-color-green .lyric-line.active {
  --highlight-color: #00cc00;
  --highlight-bg: rgba(0, 204, 0, 0.2);
}
.lyrics-content.highlight-color-purple .lyric-line.active {
  --highlight-color: #9933ff;
  --highlight-bg: rgba(153, 51, 255, 0.2);
}
</style>