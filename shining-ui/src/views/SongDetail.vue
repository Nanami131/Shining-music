<template>
  <div class="song-detail-container">
    <div v-if="isLoaded">
      <h2>{{ song.title || '未知歌曲' }}</h2>
      <div class="song-content">
        <img :src="song.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" />
        <div class="song-info">
          <p class="artist-link" @click="goToArtist"><strong>歌手：</strong>{{ artistName || '未知' }}</p>
          <p v-if="song.duration"><strong>时长：</strong>{{ formatDuration(song.duration) }}</p>
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
            <div class="lyrics-select" v-if="allLyrics.length > 1 && !bilingualMode">
              <select v-model="selectedLyricId" @change="loadSelectedLyrics">
                <option v-for="lyric in allLyrics" :key="lyric.id" :value="lyric.id">
                  {{ lyricLabel(lyric.languageMsg) }} #{{ lyric.id }}
                </option>
              </select>
            </div>
            <div class="lang-select">
              <span
                class="lang-btn"
                :class="{ active: !bilingualMode && selectedLang === 'ja', disabled: !hasLang('ja') }"
                @click="hasLang('ja') && setSingleLang('ja')"
              >
                日
              </span>
              <span
                class="lang-btn"
                :class="{ active: !bilingualMode && selectedLang === 'zh', disabled: !hasLang('zh') }"
                @click="hasLang('zh') && setSingleLang('zh')"
              >
                中
              </span>
              <span
                class="lang-btn"
                :class="{ active: !bilingualMode && selectedLang === 'en', disabled: !hasLang('en') }"
                @click="hasLang('en') && setSingleLang('en')"
              >
                英
              </span>
              <span
                class="lang-btn bilingual-btn"
                :class="{ active: bilingualMode }"
                @click="toggleBilingual"
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
          <div v-for="(line, index) in displayLyrics" :key="index" class="lyrics-group">
            <div v-if="line.break" class="lyric-break"></div>
            <div v-else class="lyric-line">
              <template v-if="bilingualMode">
                <p v-for="(lang, li) in availableLangs" :key="li"
                   :class="li === 0 ? 'lyric-primary' : 'lyric-secondary'"
                   v-show="line[lang]">{{ line[lang] }}</p>
                <p v-if="line.text" class="lyric-primary">{{ line.text }}</p>
              </template>
              <template v-else-if="line.text">
                <p>{{ line.text }}</p>
              </template>
              <template v-else>
                <p v-if="line[selectedLang]">{{ line[selectedLang] }}</p>
                <p v-else-if="getFirstLang(line)">{{ getFirstLang(line) }}</p>
              </template>
            </div>
          </div>
          <p v-if="!displayLyrics.length" class="no-lyric">
            <span>暂无歌词</span>
          </p>
        </div>
      </div>

      <!-- 歌曲标签 -->
      <div class="tags-section" v-if="tagCategories.length">
        <h3>歌曲标签</h3>
        <div class="tag-categories">
          <div
            v-for="cat in tagCategories"
            :key="cat.category"
            class="tag-category"
          >
            <div class="category-header" @click="toggleCategory(cat.category)">
              <span class="category-arrow" :class="{ open: expandedCategories[cat.category] }">▸</span>
              <span class="category-name">{{ cat.label }}</span>
              <span class="category-count">{{ cat.tags.length }} 维</span>
            </div>
            <transition name="slide">
              <div v-if="expandedCategories[cat.category]" class="category-tags">
                <div v-for="tag in cat.tags" :key="tag.name" class="tag-row">
                  <span class="tag-label">{{ tag.labelZh || tag.name }}</span>
                  <div class="tag-bar-wrapper">
                    <div class="tag-bar" :style="{ width: (tag.value * 100) + '%' }" :class="'bar-' + cat.category"></div>
                  </div>
                  <span class="tag-value">{{ tag.value.toFixed(2) }}</span>
                </div>
              </div>
            </transition>
          </div>
        </div>
      </div>
      <div class="tags-section tags-empty" v-else-if="isLoaded && tagsFetched && !tagCategories.length">
        <h3>歌曲标签</h3>
        <p class="no-tags">暂无标签数据</p>
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
import statisticsApi from '@/api/statistics';
import recommendApi from '@/api/recommend';
import defaultCover from '@/assets/default-cover.png';
import {
  parseLyrics as parseLrc,
  timeToSeconds,
  mergeMultiLang,
  detectLangs,
  normalizeLyricLang,
  orderLyricLangs,
  lyricLangLabel,
} from '@/utils/lrcParser';

export default {
  name: 'SongDetail',
  data() {
    return {
      song: null,
      allLyrics: [],
      selectedLyricId: null,
      selectedLang: 'ja',
      bilingualMode: false,
      parsedLyrics: [],
      bilingualLyrics: [],
      availableLangs: [],
      defaultCover,
      isLoaded: false,
      hasError: false,
      highlightColor: 'pink',
      userId: null,
      artistName: null,
      tagDefinitions: {},
      songTags: [],
      tagsFetched: false,
      expandedCategories: {},
    };
  },
  computed: {
    displayLyrics() {
      return this.bilingualMode ? this.bilingualLyrics : this.parsedLyrics;
    },
    tagCategories() {
      const categoryLabels = {
        language: '语言', source: '来源', mood: '情绪',
        vocal: '声线', audio: '音频特征', era: '年代',
      };
      const categoryOrder = ['language', 'source', 'mood', 'vocal', 'audio', 'era'];
      const tagValueMap = {};
      this.songTags.forEach(st => { tagValueMap[st.tagId] = st.value; });

      const result = [];
      for (const cat of categoryOrder) {
        const defs = this.tagDefinitions[cat];
        if (!defs || !defs.length) continue;
        const tags = defs.map(d => ({
          name: d.name,
          labelZh: d.labelZh,
          value: tagValueMap[d.id] !== undefined ? tagValueMap[d.id] : 0,
        }));
        const hasAnyValue = tags.some(t => t.value > 0);
        if (hasAnyValue) {
          result.push({ category: cat, label: categoryLabels[cat] || cat, tags });
        }
      }
      return result;
    },
  },
  created() {
    let userBase = {};
    try { userBase = JSON.parse(localStorage.getItem('userBase') || '{}'); } catch (e) { /* ignore */ }
    this.userId = userBase.id ?? null;
    this.loadSongDetails();
  },
  watch: {
    '$route.params.id'() {
      this.loadSongDetails();
    },
  },
  methods: {
    toggleCategory(cat) {
      this.expandedCategories[cat] = !this.expandedCategories[cat];
    },
    async fetchTags(songId) {
      try {
        const [defRes, tagRes] = await Promise.all([
          recommendApi.getAllTagDefinitions(),
          recommendApi.getSongTags(songId),
        ]);
        if (defRes.data && defRes.data.passed) {
          this.tagDefinitions = defRes.data.data || {};
        }
        if (tagRes.data && tagRes.data.passed) {
          this.songTags = tagRes.data.data || [];
        }
        this.$nextTick(() => {
          if (this.tagCategories.length) {
            this.expandedCategories[this.tagCategories[0].category] = true;
          }
        });
      } catch (e) {
        console.warn('标签加载失败', e);
      }
      this.tagsFetched = true;
    },
    async loadSongDetails() {
      this.isLoaded = false;
      this.hasError = false;
      try {
        const songId = this.$route.params.id;
        const response = await musicApi.getSongDetailsInfo(songId, this.userId);
        if (response.data.passed) {
          const songDetails = response.data.data || {};
          this.song = songDetails;
          const detailsLyrics = Array.isArray(songDetails.allLyrics) ? songDetails.allLyrics : null;
          if (detailsLyrics) {
            this.applyLyricsData(detailsLyrics);
          } else {
            await this.loadAllLyrics(songId);
          }
          await this.loadArtistName();
          this.isLoaded = true;
          this.fetchTags(songId);
          if (this.userId) {
            statisticsApi.reportEvent({
              userId: this.userId,
              eventType: 'BROWSE',
              targetType: 'song',
              targetId: Number(songId),
            }).catch(() => {});
          }
        } else {
          this.hasError = true;
          alert('获取歌曲详情失败：' + response.data.message);
        }
      } catch (error) {
        this.hasError = true;
        alert('获取歌曲详情出错：' + error.message);
      }
    },
    async loadArtistName() {
      const artistId = this.song && this.song.artistId;
      if (!artistId) {
        this.artistName = null;
        return;
      }
      try {
        const res = await musicApi.getSingerBaseInfo(artistId);
        if (res.data && res.data.passed && res.data.data) {
          const name = res.data.data.name || `Artist ${artistId}`;
          this.artistName = name;
        } else {
          this.artistName = `Artist ${artistId}`;
        }
      } catch (e) {
        this.artistName = `Artist ${artistId}`;
      }
    },
    async loadAllLyrics(songId) {
      try {
        const response = await musicApi.getAllLyrics(songId);
        if (response.data.passed && Array.isArray(response.data.data)) {
          this.applyLyricsData(response.data.data);
        } else {
          this.applyLyricsData([]);
        }
      } catch (error) {
        this.applyLyricsData([]);
      }
    },
    applyLyricsData(lyrics) {
      this.allLyrics = Array.isArray(lyrics) ? lyrics : [];
      this.selectedLyricId = null;
      this.parsedLyrics = [];
      this.bilingualLyrics = [];
      this.availableLangs = [];

      if (!this.allLyrics.length) {
        return;
      }

      this.selectedLyricId = this.allLyrics[0].id;
      const firstLang = this.allLyrics[0].languageMsg;
      if (firstLang) {
        this.selectedLang = normalizeLyricLang(firstLang);
      }
      this.loadSelectedLyrics();
    },
    loadSelectedLyrics() {
      const selectedLyric = this.allLyrics.find(lyric => lyric.id === this.selectedLyricId);
      if (selectedLyric) {
        this.parseLyrics(selectedLyric.content || '');
      } else {
        this.parsedLyrics = [];
      }
      this.buildBilingual();
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
          if (this.userId) {
            statisticsApi.reportEvent({
              userId: this.userId,
              eventType: 'FAVORITE',
              targetType: 'song',
              targetId: this.song.id,
              extraData: { action: favorite ? 'add' : 'remove' },
            }).catch(() => {});
          }
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('更新收藏状态失败：' + msg);
        }
      } catch (error) {
        alert('更新收藏状态失败：' + error.message);
      }
    },
    parseLyrics(content) {
      this.parsedLyrics = parseLrc(content);
    },
    buildBilingual() {
      const parsed = this.parsedLyrics;
      const inlineLangs = detectLangs(parsed);
      if (inlineLangs.length > 0) {
        this.bilingualLyrics = parsed;
        this.availableLangs = orderLyricLangs(inlineLangs);
        return;
      }
      if (this.allLyrics.length >= 2) {
        const sources = this.allLyrics
          .filter(l => l.languageMsg)
          .map(l => ({ lang: normalizeLyricLang(l.languageMsg), lines: parseLrc(l.content || '') }))
          .filter(s => s.lang);
        if (sources.length >= 2) {
          this.bilingualLyrics = mergeMultiLang(sources);
          this.availableLangs = orderLyricLangs(sources.map(s => s.lang));
          return;
        }
      }
      this.bilingualLyrics = parsed;
      this.availableLangs = [];
    },
    getFirstLang(line) {
      for (const k of Object.keys(line)) {
        if (k !== 'time' && k !== 'break' && line[k]) return line[k];
      }
      return '';
    },
    timeToSeconds(timeStr) {
      return timeToSeconds(timeStr);
    },
    playSong() {
      this.$bus.emit('playSong', { songId: this.song.id, source: 'songDetail' });
    },
    goToArtist() {
      if (this.song && this.song.artistId) {
        this.$router.push(`/singer/${this.song.artistId}`);
      }
    },
    formatDuration(seconds) {
      if (!seconds) return '';
      const m = Math.floor(seconds / 60);
      const s = seconds % 60;
      return `${m}:${String(s).padStart(2, '0')}`;
    },
    hasLang(lang) {
      return this.allLyrics.some(
        l => normalizeLyricLang(l.languageMsg) === lang
      );
    },
    setSingleLang(lang) {
      this.bilingualMode = false;
      this.selectedLang = lang;
      const match = this.allLyrics.find(
        l => normalizeLyricLang(l.languageMsg) === lang
      );
      if (match && match.id !== this.selectedLyricId) {
        this.selectedLyricId = match.id;
        this.loadSelectedLyrics();
      }
    },
    toggleBilingual() {
      this.bilingualMode = !this.bilingualMode;
    },
    setHighlightColor(color) {
      this.highlightColor = color;
    },
    lyricLabel(lang) {
      return lyricLangLabel(lang);
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
.artist-link {
  cursor: pointer;
  transition: color 0.2s;
}
.artist-link:hover {
  color: #4facfe;
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
.lang-btn.disabled {
  opacity: 0.3;
  cursor: not-allowed;
  pointer-events: none;
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
  font-size: 20px;
  color: #333;
  margin-bottom: 8px;
  font-family: 'LXGW WenKai', 'AR PL UKai CN', 'STKaiti', 'KaiTi', '楷体', serif;
  display: inline-block;
  width: 100%;
  padding: 8px 0;
}
.lyric-line p {
  margin: 2px 0;
}
.lyric-primary {
  font-size: 21px;
}
.lyric-secondary {
  font-size: 16px;
  opacity: 0.55;
  margin-top: 3px !important;
  letter-spacing: 0.5px;
}
.lyric-break {
  height: 24px;
}
.bilingual-btn {
  width: auto !important;
  padding: 0 10px;
  border-radius: 18px !important;
  font-size: 13px !important;
}
.lyrics-content p:not(.lyric-line p) {
  font-size: 20px;
  color: #666;
  font-family: 'LXGW WenKai', 'AR PL UKai CN', 'STKaiti', 'KaiTi', '楷体', serif;
}
.no-lyric {
  text-align: center;
  font-size: 18px;
  color: #666;
}

/* ---- 歌曲标签区 ---- */
.tags-section {
  margin-top: 30px;
  padding: 0 10px;
}
.tags-section h3 {
  font-size: 20px;
  margin-bottom: 16px;
  color: #333;
}
.tags-empty .no-tags {
  color: #999;
  font-size: 14px;
}
.tag-category {
  margin-bottom: 12px;
  border: 1px solid #eee;
  border-radius: 8px;
  overflow: hidden;
}
.category-header {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  background: #fafafa;
  cursor: pointer;
  user-select: none;
}
.category-header:hover {
  background: #f0f0f0;
}
.category-arrow {
  display: inline-block;
  transition: transform 0.2s;
  margin-right: 8px;
  font-size: 14px;
  color: #888;
}
.category-arrow.open {
  transform: rotate(90deg);
}
.category-name {
  font-weight: 600;
  font-size: 15px;
  color: #444;
}
.category-count {
  margin-left: auto;
  font-size: 12px;
  color: #aaa;
}
.category-tags {
  padding: 10px 14px;
}
.tag-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}
.tag-row:last-child {
  margin-bottom: 0;
}
.tag-label {
  width: 100px;
  flex-shrink: 0;
  font-size: 13px;
  color: #555;
  text-align: right;
  padding-right: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tag-bar-wrapper {
  flex: 1;
  height: 14px;
  background: #eee;
  border-radius: 7px;
  overflow: hidden;
}
.tag-bar {
  height: 100%;
  border-radius: 7px;
  transition: width 0.4s ease;
}
.bar-language { background: linear-gradient(90deg, #667eea, #764ba2); }
.bar-source   { background: linear-gradient(90deg, #f093fb, #f5576c); }
.bar-mood     { background: linear-gradient(90deg, #4facfe, #00f2fe); }
.bar-vocal    { background: linear-gradient(90deg, #43e97b, #38f9d7); }
.bar-audio    { background: linear-gradient(90deg, #fa709a, #fee140); }
.bar-era      { background: linear-gradient(90deg, #a18cd1, #fbc2eb); }
.tag-value {
  width: 45px;
  flex-shrink: 0;
  text-align: right;
  font-size: 12px;
  color: #888;
  padding-left: 8px;
}

.slide-enter-active, .slide-leave-active {
  transition: all 0.25s ease;
  max-height: 600px;
  overflow: hidden;
}
.slide-enter, .slide-leave-to {
  max-height: 0;
  opacity: 0;
  padding-top: 0;
  padding-bottom: 0;
}
</style>
