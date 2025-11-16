<template>
  <div class="bottom-bar" :class="{ expanded: showLyrics }">
    <div class="fixed-bar">
      <div class="song-info">
        <img :src="currentSong.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" />
        <div class="song-details">
          <span>{{ currentSong.title || '未知歌曲' }}</span>
          <span class="artist">歌手 ID: {{ currentSong.artistId || '未知' }}</span>
        </div>
        <button
          class="favorite-btn"
          :class="{ active: currentSong && currentSong.favorite }"
          @click.stop="toggleFavoriteFromPlayer"
          :title="currentSong && currentSong.favorite ? '取消收藏' : '收藏歌曲'"
        >
          <span class="heart-icon"></span>
        </button>
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
          <button class="icon-btn" @click="playPrev" :disabled="!hasPrev">
            <span class="icon prev"></span>
          </button>
          <button class="icon-btn play-btn" @click="togglePlay" :disabled="!audio.src">
            <span class="icon" :class="isPlaying ? 'pause' : 'play'"></span>
          </button>
          <button class="icon-btn" @click="playNext" :disabled="!hasNext">
            <span class="icon next"></span>
          </button>

          <button class="mode-btn" @click="cyclePlayMode" :title="playModeLabel">
            <span v-if="playMode === 'single'" class="mode-icon">单曲循环</span>
            <span v-else-if="playMode === 'sequential'" class="mode-icon">顺序播放</span>
            <span v-else class="mode-icon">播完停止</span>
          </button>
        </div>
      </div>

      <button class="toggle-lyrics" @click="toggleLyrics">
        {{ showLyrics ? '收起歌词' : '展开歌词' }}
      </button>
    </div>

    <div class="lyrics-panel" v-if="showLyrics">
      <div class="panel-content">
        <div class="playlist-placeholder"></div>
        <div class="lyrics-right">
          <div class="lyric-header">
            <div class="title">歌词</div>
            <div class="controls">
              <div class="lyrics-select">
                <select v-model="selectedLyricId" @change="loadSelectedLyrics">
                  <option v-for="lyric in allLyrics" :key="lyric.id" :value="lyric.id">
                    版本 {{ lyric.id }}
                  </option>
                </select>
              </div>
              <div class="lang-select">
                <span class="lang-btn" :class="{ active: selectedLang === 'zh' }" @click="setLanguage('zh')">中</span>
                <span class="lang-btn" :class="{ active: selectedLang === 'ja' }" @click="setLanguage('ja')">日</span>
                <span class="lang-btn" :class="{ active: selectedLang === 'all' }" @click="setLanguage('all')">全</span>
              </div>
              <div class="color-select">
                <span class="color-btn pink" :class="{ active: highlightColor === 'pink' }" @click="setHighlightColor('pink')"></span>
                <span class="color-btn blue" :class="{ active: highlightColor === 'blue' }" @click="setHighlightColor('blue')"></span>
                <span class="color-btn green" :class="{ active: highlightColor === 'green' }" @click="setHighlightColor('green')"></span>
                <span class="color-btn purple" :class="{ active: highlightColor === 'purple' }" @click="setHighlightColor('purple')"></span>
              </div>
            </div>
          </div>
          <div class="lyrics-content" ref="lyricsContent" :class="'highlight-color-' + highlightColor">
            <div v-for="(line, index) in parsedLyrics" :key="index" class="lyrics-group">
              <div
                :class="{ active: isActiveLine(line.time, index) }"
                class="lyric-line"
                ref="lyricLines"
              >
                <template v-if="selectedLang === 'all'">
                  <p v-if="line.zh">{{ line.zh }}</p>
                  <p v-if="line.ja">{{ line.ja }}</p>
                  <p v-if="!line.zh && !line.ja">暂无对应歌词</p>
                </template>
                <template v-else>
                  <p v-if="line[selectedLang]">{{ line[selectedLang] }}</p>
                  <p v-else>暂无对应歌词</p>
                </template>
              </div>
            </div>
            <p v-if="!parsedLyrics.length" class="no-lyric"><span>暂无歌词</span></p>
          </div>
        </div>
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
      allLyrics: [],
      selectedLyricId: null,
      selectedLang: 'zh',
      parsedLyrics: [],
      audio: new Audio(),
      isPlaying: false,
      currentTime: 0,
      duration: 0,
      showLyrics: false,
      defaultCover,
      highlightColor: 'pink',
      userId: null,
      playMode: 'stop', // stop | sequential | single
      playlist: [],
      currentIndex: -1,
    };
  },
  computed: {
    hasPrev() {
      return this.playlist.length > 0 && this.currentIndex > 0;
    },
    hasNext() {
      return (
        this.playlist.length > 0 &&
        this.currentIndex >= 0 &&
        this.currentIndex < this.playlist.length - 1
      );
    },
    playModeLabel() {
      if (this.playMode === 'single') return '单曲循环';
      if (this.playMode === 'sequential') return '顺序播放';
      return '播完停止';
    },
  },
  created() {
    const userBase = JSON.parse(localStorage.getItem('userBase') || '{}');
    this.userId = userBase.id ?? null;
    this.audio.addEventListener('timeupdate', this.updateProgress);
    this.audio.addEventListener('loadedmetadata', this.updateDuration);
    this.audio.addEventListener('ended', this.handleEnded);
    this.$bus.on('playSong', this.handlePlaySongEvent);
  },
  beforeDestroy() {
    this.audio.removeEventListener('timeupdate', this.updateProgress);
    this.audio.removeEventListener('loadedmetadata', this.updateDuration);
    this.audio.removeEventListener('ended', this.handleEnded);
    this.$bus.off('playSong', this.handlePlaySongEvent);
    this.audio.pause();
    this.audio.src = '';
  },
  methods: {
    async handlePlaySongEvent({ songId, playlist, index }) {
      if (Array.isArray(playlist)) {
        this.playlist = playlist;
        this.currentIndex =
          typeof index === 'number' ? index : playlist.findIndex(id => id === songId);
      }
      await this.playSong(songId);
    },
    async playSong(songId) {
      try {
        this.audio.pause();
        this.audio.src = '';
        this.currentTime = 0;
        this.isPlaying = false;

        const response = await musicApi.playSong(songId, this.userId);
        if (response.data.passed) {
          this.currentSong = response.data.data;
          this.audio.src = this.currentSong.fileUrl || '';
          this.audio.play();
          this.isPlaying = true;
          this.loadAllLyrics(songId);
        } else {
          alert('获取歌曲信息失败：' + response.data.message);
        }
      } catch (error) {
        alert('播放歌曲失败：' + error.message);
      }
    },
    playPrev() {
      if (!this.hasPrev) return;
      this.currentIndex -= 1;
      const prevId = this.playlist[this.currentIndex];
      if (prevId != null) {
        this.playSong(prevId);
      }
    },
    playNext() {
      if (!this.hasNext) return;
      this.currentIndex += 1;
      const nextId = this.playlist[this.currentIndex];
      if (nextId != null) {
        this.playSong(nextId);
      }
    },
    handleEnded() {
      if (this.playMode === 'single') {
        if (this.currentSong && this.currentSong.id) {
          this.playSong(this.currentSong.id);
        }
      } else if (this.playMode === 'sequential') {
        if (this.hasNext) {
          this.playNext();
        } else {
          this.isPlaying = false;
          this.currentTime = 0;
        }
      } else {
        this.isPlaying = false;
        this.currentTime = 0;
      }
    },
    cyclePlayMode() {
      if (this.playMode === 'stop') {
        this.playMode = 'sequential';
      } else if (this.playMode === 'sequential') {
        this.playMode = 'single';
      } else {
        this.playMode = 'stop';
      }
    },
    async toggleFavoriteFromPlayer() {
      if (!this.currentSong || !this.currentSong.id) {
        return;
      }
      if (!this.userId) {
        alert('请先登录再收藏歌曲');
        return;
      }
      try {
        const response = await musicApi.toggleFavoriteSong({
          userId: this.userId,
          songId: this.currentSong.id,
        });
        if (response.data && response.data.passed) {
          const favorite = response.data.data?.favorite ?? false;
          this.currentSong.favorite = favorite;
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('更新收藏状态失败：' + msg);
        }
      } catch (error) {
        alert('更新收藏状态失败：' + error.message);
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
        }
      } catch (error) {
        this.allLyrics = [];
        this.selectedLyricId = null;
        this.parsedLyrics = [];
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
      const activeLine = this.$refs.lyricLines && this.$refs.lyricLines[index];
      if (lyricsContent && activeLine) {
        const scrollTop = activeLine.offsetTop - lyricsContent.offsetTop - 50;
        if (scrollTop >= 0 && scrollTop <= lyricsContent.scrollHeight - lyricsContent.clientHeight) {
          lyricsContent.scrollTo({
            top: scrollTop,
            behavior: 'smooth',
          });
        }
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
      this.currentTime = this.audio.currentTime || 0;
    },
    updateDuration() {
      this.duration = this.audio.duration || 0;
    },
    seek() {
      if (this.audio.src) {
        this.audio.currentTime = this.currentTime;
      }
    },
    toggleLyrics() {
      this.showLyrics = !this.showLyrics;
    },
    setLanguage(lang) {
      this.selectedLang = lang;
    },
    setHighlightColor(color) {
      this.highlightColor = color;
    },
    formatTime(time) {
      if (!time || isNaN(time)) return '00:00';
      const minutes = Math.floor(time / 60)
        .toString()
        .padStart(2, '0');
      const seconds = Math.floor(time % 60)
        .toString()
        .padStart(2, '0');
      return `${minutes}:${seconds}`;
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
  height: 90px;
  background: #fff;
  box-shadow: 0 -2px 4px rgba(0, 0, 0, 0.1);
  z-index: 1000;
  transition: height 0.3s ease;
}
.bottom-bar.expanded {
  height: 290px;
}
.fixed-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  padding: 10px 20px;
  gap: 20px;
  height: 90px;
  z-index: 1;
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
.favorite-btn {
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
  width: 20px;
  height: 20px;
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
  gap: 8px;
  justify-content: center;
  align-items: center;
}
.icon-btn {
  width: 40px;
  height: 40px;
  border-radius: 20px;
  border: 1px solid rgba(148, 163, 184, 0.7);
  background: transparent;
  box-shadow: 0 0 10px rgba(148, 163, 184, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
  backdrop-filter: blur(4px);
}
.icon-btn:hover {
  box-shadow: 0 0 14px rgba(79, 172, 254, 0.55);
  border-color: rgba(79, 172, 254, 0.9);
}
.icon-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  box-shadow: none;
}
.play-btn {
  width: 46px;
  height: 46px;
  border-radius: 23px;
  border-color: rgba(79, 172, 254, 0.9);
  box-shadow: 0 0 16px rgba(79, 172, 254, 0.6);
}
.icon {
  display: block;
  width: 24px;
  height: 24px;
  background-repeat: no-repeat;
  background-position: center;
  background-size: contain;
}
.icon.play {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M9 6l8 6-8 6z' fill='none' stroke='%234facfe' stroke-width='2.2' stroke-linejoin='round'/%3E%3C/svg%3E");
}
.icon.pause {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Crect x='7' y='6' width='3.5' height='12' rx='1.2' fill='none' stroke='%234facfe' stroke-width='2'/%3E%3Crect x='13.5' y='6' width='3.5' height='12' rx='1.2' fill='none' stroke='%234facfe' stroke-width='2'/%3E%3C/svg%3E");
}
.icon.prev,
.icon.next {
  width: 24px;
  height: 24px;
}
.icon.prev {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M9 6v12' stroke='%234b5563' stroke-width='2' stroke-linecap='round'/%3E%3Cpath d='M17 6l-7 6 7 6z' fill='none' stroke='%234b5563' stroke-width='2' stroke-linejoin='round'/%3E%3C/svg%3E");
}
.icon.next {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M15 6v12' stroke='%234b5563' stroke-width='2' stroke-linecap='round'/%3E%3Cpath d='M7 6l7 6-7 6z' fill='none' stroke='%234b5563' stroke-width='2' stroke-linejoin='round'/%3E%3C/svg%3E");
}
.mode-btn {
  padding: 4px 10px;
  border-radius: 999px;
  border: none;
  background: #f1f5f9;
  color: #1e293b;
  font-size: 12px;
  cursor: pointer;
}
.mode-icon {
  font-size: 12px;
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
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  max-height: 200px;
  width: 100%;
  z-index: 2;
  display: flex;
  flex-direction: column;
}
.panel-content {
  display: flex;
  flex-direction: row;
  height: 200px;
}
.playlist-placeholder {
  width: 30%;
  background: #fefeff;
}
.lyrics-right {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.lyric-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px 0;
}
.lyric-header .title {
  font-weight: bold;
}
.lyric-header .controls {
  display: flex;
  gap: 10px;
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
  gap: 6px;
}
.lang-btn {
  width: 28px;
  height: 28px;
  line-height: 28px;
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

.lyrics-content {
  max-height: 140px;
  padding: 20px;
  overflow-y: auto;
  background: #edf1f6;
  text-align: center;
}
.lyrics-content.highlight-color-pink .lyric-line {
  color: #ff6b81;
}
.lyrics-content.highlight-color-blue .lyric-line {
  color: #3498db;
}
.lyrics-content.highlight-color-green .lyric-line {
  color: #2ecc71;
}
.lyrics-content.highlight-color-purple .lyric-line {
  color: #9b59b6;
}
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

.lyrics-group {
  margin-bottom: 20px;
}
.lyric-line {
  font-size: 16px;
  color: #333;
  margin-bottom: 8px;
  font-family: 'KaiTi', 'STKaiti', '楷体', sans-serif;
  display: inline-block;
}
.lyric-line.active {
  color: var(--highlight-color);
  background: var(--highlight-bg);
  font-weight: 600;
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

