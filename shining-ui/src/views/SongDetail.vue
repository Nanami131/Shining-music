<template>
  <div class="song-detail-container">
    <div v-if="isLoaded">
      <h2>{{ song.title || '未知歌曲' }}</h2>
      <div class="song-content">
        <img :src="song.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" />
        <div class="song-info">
          <p><strong>歌手 ID：</strong>{{ song.artistId || '未知' }}</p>
          <p><strong>专辑 ID：</strong>{{ song.albumId || '未知' }}</p>
          <p><strong>状态：</strong>{{ song.status || '未知' }}</p>
          <p><strong>创建时间：</strong>{{ song.createdAt || '未知' }}</p>
          <p><strong>更新时间：</strong>{{ song.updatedAt || '未知' }}</p>
          <div class="action-buttons">
            <button class="play-btn" @click="playSong">播放</button>
            <button
              class="favorite-btn"
              :class="{ active: song && song.favorite }"
              @click="toggleFavorite"
              :title="song && song.favorite ? '取消收藏' : '收藏歌曲'"
            >
              <span class="heart-icon"></span>
            </button>
          </div>
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
              <span
                class="lang-btn"
                :class="{ active: selectedLang === 'zh' }"
                @click="setLanguage('zh')"
              >
                中
              </span>
              <span
                class="lang-btn"
                :class="{ active: selectedLang === 'ja' }"
                @click="setLanguage('ja')"
              >
                日
              </span>
              <span
                class="lang-btn"
                :class="{ active: selectedLang === 'all' }"
                @click="setLanguage('all')"
              >
                全
              </span>
            </div>
            <div class="color-select">
              <span
                class="color-btn pink"
                :class="{ active: highlightColor === 'pink' }"
                @click="setHighlightColor('pink')"
              ></span>
              <span
                class="color-btn blue"
                :class="{ active: highlightColor === 'blue' }"
                @click="setHighlightColor('blue')"
              ></span>
              <span
                class="color-btn green"
                :class="{ active: highlightColor === 'green' }"
                @click="setHighlightColor('green')"
              ></span>
              <span
                class="color-btn purple"
                :class="{ active: highlightColor === 'purple' }"
                @click="setHighlightColor('purple')"
              ></span>
            </div>
          </div>
        </div>
        <div class="lyrics-content" :class="'highlight-color-' + highlightColor">
          <div v-for="(line, index) in parsedLyrics" :key="index" class="lyrics-group">
            <div class="lyric-line">
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
          <p v-if="!parsedLyrics.length" class="no-lyric">
            <span>暂无歌词</span>
          </p>
        </div>
      </div>
    </div>
    <div v-else-if="hasError">
      <h2>歌曲信息加载失败</h2>
      <p>请稍后重试。</p>
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
      song: null,
      allLyrics: [],
      selectedLyricId: null,
      selectedLang: 'zh',
      parsedLyrics: [],
      defaultCover,
      isLoaded: false,
      hasError: false,
      highlightColor: 'pink',
      userId: null,
    };
  },
  created() {
    const userBase = JSON.parse(localStorage.getItem('userBase') || '{}');
    this.userId = userBase.id ?? null;
    this.loadSongDetails();
  },
  methods: {
    async loadSongDetails() {
      this.isLoaded = false;
      this.hasError = false;
      try {
        const songId = this.$route.params.id;
        const response = await musicApi.getSongDetailsInfo(songId, this.userId);
        if (response.data.passed) {
          this.song = response.data.data;
          await this.loadAllLyrics(songId);
          this.isLoaded = true;
        } else {
          this.hasError = true;
          alert('获取歌曲详情失败：' + response.data.message);
        }
      } catch (error) {
        this.hasError = true;
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
          alert('暂无歌词数据');
        }
      } catch (error) {
        this.allLyrics = [];
        this.selectedLyricId = null;
        this.parsedLyrics = [];
        alert('加载歌词失败：' + error.message);
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
    async toggleFavorite() {
      if (!this.song || !this.song.id) {
        return;
      }
      if (!this.userId) {
        alert('请先登录后再收藏歌曲');
        return;
      }
      try {
        const response = await musicApi.toggleFavoriteSong({
          userId: this.userId,
          songId: this.song.id,
        });
        if (response.data && response.data.passed) {
          const favorite = response.data.data?.favorite ?? false;
          this.song.favorite = favorite;
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('更新收藏状态失败：' + msg);
        }
      } catch (error) {
        alert('更新收藏状态失败：' + error.message);
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
    setHighlightColor(color) {
      this.highlightColor = color;
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
.action-buttons {
  display: flex;
  gap: 12px;
  margin-top: 10px;
  align-items: center;
}
.play-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 4px;
  background: linear-gradient(to right, #4facfe, #00f2fe);
  color: white;
  cursor: pointer;
}
.play-btn:hover {
  transform: scale(1.05);
}
.favorite-btn {
  width: 44px;
  height: 44px;
  border-radius: 50%;
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
.color-select {
  display: flex;
  gap: 10px;
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
  max-height: 400px;
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

