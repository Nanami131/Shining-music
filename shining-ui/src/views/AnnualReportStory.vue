<template>
  <div class="story-container" @click="handleTap">
    <div v-if="!userId" class="story-login">
      <p>请先登录查看年度报告</p>
      <button class="story-btn" @click.stop="$router.push('/login')">去登录</button>
    </div>

    <div v-else-if="loading" class="story-loading">
      <div class="loading-ring"></div>
      <p class="loading-text">正在回忆你的 {{ year }} ...</p>
    </div>

    <div v-else-if="error" class="story-error">
      <p>{{ error }}</p>
      <button class="story-btn" @click.stop="loadReport">重试</button>
    </div>

    <div v-else-if="report" class="story-slides">
      <transition :name="slideDirection" mode="out-in">
        <div class="slide" :key="step">

          <div v-if="step === 0" class="slide-content slide-intro">
            <div class="intro-year">{{ year }}</div>
            <h1 class="intro-title">你的年度音乐旅程</h1>
            <p class="intro-hint">点击屏幕继续</p>
          </div>

          <div v-else-if="step === 1" class="slide-content slide-stats">
            <h2 class="slide-heading">这一年</h2>
            <div class="big-stat">
              <span class="big-number">{{ report.totalPlayCount || 0 }}</span>
              <span class="big-label">次播放</span>
            </div>
            <div class="big-stat">
              <span class="big-number">{{ formatDuration(report.totalListenDuration || 0) }}</span>
              <span class="big-label">的陪伴</span>
            </div>
            <div class="big-stat">
              <span class="big-number">{{ report.totalSongCount || 0 }}</span>
              <span class="big-label">首不同的歌</span>
            </div>
          </div>

          <div v-else-if="step === 2 && report.favoriteSinger" class="slide-content slide-singer">
            <h2 class="slide-heading">你最爱的歌手</h2>
            <img
              :src="report.favoriteSinger.avatarUrl || defaultCover"
              class="singer-big-avatar"
              alt="歌手"
            />
            <div class="singer-big-name">{{ report.favoriteSinger.singerName }}</div>
            <p class="singer-big-detail">
              播放了 {{ report.favoriteSinger.playCount }} 次 ·
              共 {{ formatDuration(report.favoriteSinger.totalDuration || 0) }}
            </p>
          </div>
          <div v-else-if="step === 2 && !report.favoriteSinger" class="slide-content slide-singer">
            <h2 class="slide-heading">歌手偏好</h2>
            <p class="slide-empty">今年还没有最爱的歌手呢</p>
          </div>

          <div v-else-if="step === 3" class="slide-content slide-top-songs">
            <h2 class="slide-heading">年度单曲</h2>
            <div v-if="report.topSongs && report.topSongs.length" class="top-songs-story">
              <div
                v-for="(song, idx) in report.topSongs.slice(0, 3)"
                :key="song.songId"
                class="top-song-story-item"
              >
                <span class="top-song-story-rank">{{ idx + 1 }}</span>
                <img :src="song.coverUrl || defaultCover" class="top-song-story-cover" alt="" />
                <div class="top-song-story-info">
                  <span class="top-song-story-title">{{ song.title }}</span>
                  <span class="top-song-story-artist">{{ song.singerName }}</span>
                </div>
                <span class="top-song-story-count">{{ song.playCount }}次</span>
              </div>
            </div>
            <p v-else class="slide-empty">今年还没有最爱的单曲呢</p>
          </div>

          <div v-else-if="step === 4" class="slide-content slide-habits">
            <h2 class="slide-heading">听歌习惯</h2>
            <div v-if="report.maxStreak > 0" class="habit-item">
              <span class="habit-icon">&#128293;</span>
              <span>最长连续听歌 <strong>{{ report.maxStreak }}</strong> 天</span>
            </div>
            <div v-if="peakHour != null" class="habit-item">
              <span class="habit-icon">&#127769;</span>
              <span>最爱在 <strong>{{ peakHour }}:00</strong> 听歌</span>
            </div>
            <div v-if="report.avgCompletionRate != null" class="habit-item">
              <span class="habit-icon">&#10004;</span>
              <span>平均完播率 <strong>{{ report.avgCompletionRate }}%</strong></span>
            </div>
          </div>

          <div v-else-if="step === 5" class="slide-content slide-personality">
            <h2 class="slide-heading">你的音乐人格</h2>
            <div v-if="report.musicPersonality" class="personality-badge">
              {{ report.musicPersonality }}
            </div>
            <div v-if="report.languageDistribution && report.languageDistribution.length" class="lang-pills">
              <span
                v-for="lang in report.languageDistribution"
                :key="lang.language"
                class="lang-pill"
              >
                {{ lang.language }} {{ lang.songCount }}首
              </span>
            </div>
            <div v-if="report.moodDistribution" class="mood-list">
              <div
                v-for="(value, key) in report.moodDistribution"
                :key="key"
                class="mood-bar-item"
              >
                <span class="mood-bar-label">{{ moodLabels[key] || key }}</span>
                <div class="mood-bar-track">
                  <div class="mood-bar-fill" :style="{ width: (value * 100) + '%' }"></div>
                </div>
              </div>
            </div>
            <p v-if="!report.musicPersonality && !report.moodDistribution" class="slide-empty">
              听更多歌曲解锁你的音乐人格
            </p>
          </div>

          <div v-else-if="step === 6" class="slide-content slide-ending">
            <h2 class="slide-heading">{{ year }} 回顾完毕</h2>
            <p class="ending-text">音乐不停，故事继续</p>
            <button class="story-btn primary" @click.stop="goToFullReport">
              查看完整报告
            </button>
            <button class="story-btn secondary" @click.stop="$router.back()">
              返回
            </button>
          </div>

        </div>
      </transition>

      <div class="story-progress">
        <div
          v-for="i in totalSteps"
          :key="i"
          class="progress-dot"
          :class="{ active: i - 1 === step, done: i - 1 < step }"
        ></div>
      </div>

      <div class="story-nav" v-if="step > 0 && step < totalSteps - 1">
        <button class="nav-arrow left" @click.stop="prev">&lt;</button>
        <button class="nav-arrow right" @click.stop="next">&gt;</button>
      </div>
    </div>
  </div>
</template>

<script>
import statisticsApi from '@/api/statistics';
import defaultCover from '@/assets/default-cover.png';

export default {
  name: 'AnnualReportStory',
  data() {
    return {
      userId: null,
      year: new Date().getFullYear(),
      report: null,
      loading: false,
      error: null,
      defaultCover,
      step: 0,
      totalSteps: 7,
      slideDirection: 'slide-left',
      moodLabels: {
        Calm: '平静', Energetic: '活力', Melancholic: '忧郁',
        Joyful: '欢快', Tense: '紧张', Romantic: '浪漫',
      },
    };
  },
  computed: {
    peakHour() {
      if (!this.report?.hourlyDistribution) return null;
      const dist = this.report.hourlyDistribution;
      let maxVal = 0, maxIdx = null;
      dist.forEach((v, i) => { if (v > maxVal) { maxVal = v; maxIdx = i; } });
      return maxVal > 0 ? maxIdx : null;
    },
  },
  created() {
    let userBase = {};
    try { userBase = JSON.parse(localStorage.getItem('userBase') || '{}'); } catch (e) { /* ignore */ }
    this.userId = userBase.id ?? null;
    if (this.$route.query.year) {
      this.year = parseInt(this.$route.query.year, 10) || new Date().getFullYear();
    }
    if (this.userId) {
      this.loadReport();
    }
  },
  mounted() {
    this._keyHandler = (e) => {
      if (e.key === 'ArrowRight' || e.key === ' ') this.next();
      if (e.key === 'ArrowLeft') this.prev();
    };
    window.addEventListener('keydown', this._keyHandler);
  },
  beforeUnmount() {
    if (this._keyHandler) window.removeEventListener('keydown', this._keyHandler);
  },
  methods: {
    async loadReport() {
      this.loading = true;
      this.error = null;
      try {
        const res = await statisticsApi.getAnnualReport(this.userId, this.year);
        if (res.data?.passed && res.data.data) {
          this.report = res.data.data;
        } else {
          this.error = res.data?.message || '加载报告失败';
        }
      } catch (e) {
        this.error = '加载报告失败：' + e.message;
      } finally {
        this.loading = false;
      }
    },
    next() {
      if (this.step < this.totalSteps - 1) {
        this.slideDirection = 'slide-left';
        this.step++;
      }
    },
    prev() {
      if (this.step > 0) {
        this.slideDirection = 'slide-right';
        this.step--;
      }
    },
    handleTap() {
      if (this.step < this.totalSteps - 1) {
        this.next();
      }
    },
    formatDuration(seconds) {
      if (!seconds || seconds <= 0) return '0分钟';
      const hours = Math.floor(seconds / 3600);
      const minutes = Math.floor((seconds % 3600) / 60);
      if (hours > 0) return `${hours}小时${minutes}分钟`;
      return `${minutes}分钟`;
    },
    goToFullReport() {
      this.$router.push({ path: '/annual-report', query: { year: this.year } });
    },
  },
};
</script>

<style scoped>
.story-container {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: linear-gradient(135deg, #0f0c29, #302b63, #24243e);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  user-select: none;
  cursor: pointer;
}

.story-login,
.story-loading,
.story-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  text-align: center;
  padding: 40px;
}

.loading-ring {
  width: 48px;
  height: 48px;
  border: 3px solid rgba(255, 255, 255, 0.15);
  border-top-color: #f093fb;
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.loading-text {
  font-size: 16px;
  opacity: 0.7;
}

.story-slides {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.slide {
  width: 100%;
  max-width: 680px;
  padding: 40px 32px;
  text-align: center;
}

.slide-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.slide-heading {
  font-size: 16px;
  text-transform: uppercase;
  letter-spacing: 4px;
  opacity: 0.5;
  font-weight: 400;
  margin: 0;
}

.slide-empty {
  opacity: 0.5;
  font-size: 15px;
}

/* Slide 0: Intro */
.intro-year {
  font-size: 120px;
  font-weight: 900;
  background: linear-gradient(135deg, #667eea, #f093fb);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  line-height: 1;
}

.intro-title {
  font-size: 32px;
  font-weight: 600;
  margin: 0;
}

.intro-hint {
  font-size: 14px;
  opacity: 0.4;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 0.7; }
}

/* Slide 1: Stats */
.big-stat {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.big-number {
  font-size: 64px;
  font-weight: 800;
  background: linear-gradient(135deg, #4facfe, #00f2fe);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.big-label {
  font-size: 22px;
  opacity: 0.7;
}

/* Slide 2: Singer */
.singer-big-avatar {
  width: 160px;
  height: 160px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
}

.singer-big-name {
  font-size: 36px;
  font-weight: 700;
}

.singer-big-detail {
  font-size: 14px;
  opacity: 0.6;
}

/* Slide 3: Top Songs */
.top-songs-story {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.top-song-story-item {
  display: flex;
  align-items: center;
  gap: 16px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 16px 20px;
  text-align: left;
}

.top-song-story-rank {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

.top-song-story-cover {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  object-fit: cover;
  flex-shrink: 0;
}

.top-song-story-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.top-song-story-title {
  font-size: 17px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.top-song-story-artist {
  font-size: 12px;
  opacity: 0.5;
}

.top-song-story-count {
  font-size: 13px;
  opacity: 0.6;
  flex-shrink: 0;
}

/* Slide 4: Habits */
.habit-item {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 22px;
  padding: 16px 0;
}

.habit-icon {
  font-size: 36px;
}

.habit-item strong {
  background: linear-gradient(135deg, #fda085, #f5576c);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* Slide 5: Personality */
.personality-badge {
  font-size: 42px;
  font-weight: 800;
  background: linear-gradient(135deg, #f093fb, #f5576c, #fda085);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  padding: 12px 0;
}

.lang-pills {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}

.lang-pill {
  padding: 6px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.1);
  font-size: 13px;
  border: 1px solid rgba(255, 255, 255, 0.15);
}

.mood-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 8px;
}

.mood-bar-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mood-bar-label {
  width: 56px;
  font-size: 15px;
  text-align: right;
  opacity: 0.7;
  flex-shrink: 0;
}

.mood-bar-track {
  flex: 1;
  height: 12px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.1);
  overflow: hidden;
}

.mood-bar-fill {
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #667eea, #f093fb);
  transition: width 0.8s ease;
}

/* Slide 6: Ending */
.ending-text {
  font-size: 22px;
  opacity: 0.6;
  margin: 0;
}

/* Buttons */
.story-btn {
  padding: 12px 32px;
  border-radius: 999px;
  border: none;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.story-btn.primary {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.4);
}

.story-btn.primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(102, 126, 234, 0.5);
}

.story-btn.secondary {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.story-btn.secondary:hover {
  background: rgba(255, 255, 255, 0.15);
}

/* Progress dots */
.story-progress {
  position: fixed;
  top: 24px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  z-index: 10;
}

.progress-dot {
  width: 8px;
  height: 8px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.2);
  transition: all 0.3s;
}

.progress-dot.active {
  width: 24px;
  background: #f093fb;
}

.progress-dot.done {
  background: rgba(255, 255, 255, 0.5);
}

/* Navigation arrows */
.story-nav {
  position: fixed;
  bottom: 40px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 24px;
  z-index: 10;
}

.nav-arrow {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: rgba(255, 255, 255, 0.05);
  color: #fff;
  font-size: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.nav-arrow:hover {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.4);
}

/* Slide transitions */
.slide-left-enter-active,
.slide-left-leave-active,
.slide-right-enter-active,
.slide-right-leave-active {
  transition: all 0.4s ease;
}

.slide-left-enter-from {
  transform: translateX(60px);
  opacity: 0;
}

.slide-left-leave-to {
  transform: translateX(-60px);
  opacity: 0;
}

.slide-right-enter-from {
  transform: translateX(-60px);
  opacity: 0;
}

.slide-right-leave-to {
  transform: translateX(60px);
  opacity: 0;
}
</style>
