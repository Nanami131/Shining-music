<template>
  <div class="annual-report-container">
    <div v-if="!userId" class="empty-tip">请先登录查看年度报告</div>
    <div v-else-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
      <p>正在生成你的年度报告...</p>
    </div>
    <div v-else-if="error" class="empty-tip">{{ error }}</div>
    <div v-else-if="report" class="report-content">

      <section class="hero-section">
        <div class="hero-bg"></div>
        <div class="hero-inner">
          <div class="year-selector">
            <button class="year-btn" :class="{ disabled: year <= 2024 }" @click="changeYear(-1)">&lt;</button>
            <span class="year-display">{{ year }}</span>
            <button class="year-btn" :class="{ disabled: year >= currentYear }" @click="changeYear(1)">&gt;</button>
          </div>
          <h1 class="hero-title">你的年度音乐旅程</h1>
          <p class="hero-subtitle" v-if="report.musicPersonality">
            你是「<strong>{{ report.musicPersonality }}</strong>」
          </p>
          <button class="replay-btn" @click="$router.push({ path: '/annual-report/story', query: { year } })">
            重新播放展示
          </button>
        </div>
      </section>

      <section class="stats-overview">
        <div class="stat-card stat-plays">
          <div class="stat-icon">&#9654;</div>
          <div class="stat-value">{{ report.totalPlayCount || 0 }}</div>
          <div class="stat-label">总播放次数</div>
        </div>
        <div class="stat-card stat-duration">
          <div class="stat-icon">&#9200;</div>
          <div class="stat-value">{{ formatDuration(report.totalListenDuration || 0) }}</div>
          <div class="stat-label">累计听歌时长</div>
        </div>
        <div class="stat-card stat-songs">
          <div class="stat-icon">&#9835;</div>
          <div class="stat-value">{{ report.totalSongCount || 0 }}</div>
          <div class="stat-label">听过的歌曲</div>
        </div>
        <div class="stat-card stat-streak">
          <div class="stat-icon">&#128293;</div>
          <div class="stat-value">{{ report.maxStreak || 0 }} 天</div>
          <div class="stat-label">最长连续听歌</div>
        </div>
        <div class="stat-card stat-completion" v-if="report.avgCompletionRate != null">
          <div class="stat-icon">&#10004;</div>
          <div class="stat-value">{{ report.avgCompletionRate }}%</div>
          <div class="stat-label">平均完播率</div>
        </div>
      </section>

      <section class="favorite-singer-section" v-if="report.favoriteSinger">
        <h2 class="section-title">最爱歌手</h2>
        <div class="favorite-singer-card">
          <img
            :src="report.favoriteSinger.avatarUrl || defaultCover"
            class="singer-avatar"
            alt="歌手头像"
          />
          <div class="singer-details">
            <h3>{{ report.favoriteSinger.singerName }}</h3>
            <p>播放 {{ report.favoriteSinger.playCount }} 次 · 共 {{ formatDuration(report.favoriteSinger.totalDuration || 0) }}</p>
          </div>
        </div>
      </section>

      <section class="top-songs-section" v-if="report.topSongs && report.topSongs.length">
        <h2 class="section-title">最爱歌曲 TOP {{ report.topSongs.length }}</h2>
        <ul class="top-list">
          <li v-for="(song, idx) in report.topSongs" :key="song.songId" class="top-item" @click="goToSong(song.songId)">
            <div class="top-rank" :class="'rank-' + (idx + 1)">{{ idx + 1 }}</div>
            <img :src="song.coverUrl || defaultCover" class="top-cover" alt="封面" />
            <div class="top-info">
              <p class="top-name">{{ song.title || '未知歌曲' }}</p>
              <p class="top-meta">{{ song.singerName || '未知歌手' }} · 播放 {{ song.playCount }} 次</p>
            </div>
          </li>
        </ul>
      </section>

      <section class="top-singers-section" v-if="report.topSingers && report.topSingers.length">
        <h2 class="section-title">最爱歌手 TOP {{ report.topSingers.length }}</h2>
        <div class="singers-grid">
          <div v-for="(singer, idx) in report.topSingers" :key="singer.singerId" class="singer-card">
            <div class="singer-rank">{{ idx + 1 }}</div>
            <img :src="singer.avatarUrl || defaultCover" class="singer-card-avatar" alt="歌手" />
            <p class="singer-card-name">{{ singer.singerName }}</p>
            <p class="singer-card-meta">{{ singer.playCount }} 次 · {{ formatDuration(singer.totalDuration || 0) }}</p>
          </div>
        </div>
      </section>

      <section class="charts-section">
        <div class="chart-row">
          <div class="chart-panel">
            <h3 class="chart-title">月度播放趋势</h3>
            <div ref="monthlyChart" class="chart-canvas"></div>
          </div>
          <div class="chart-panel">
            <h3 class="chart-title">每日听歌时段</h3>
            <div ref="hourlyChart" class="chart-canvas"></div>
          </div>
        </div>
        <div class="chart-row">
          <div class="chart-panel" v-if="report.languageDistribution && report.languageDistribution.length">
            <h3 class="chart-title">语言偏好</h3>
            <div ref="languageChart" class="chart-canvas"></div>
          </div>
          <div class="chart-panel" v-if="report.moodDistribution && Object.keys(report.moodDistribution).length">
            <h3 class="chart-title">心情画像</h3>
            <div ref="moodChart" class="chart-canvas"></div>
          </div>
        </div>
      </section>

    </div>
  </div>
</template>

<script>
import statisticsApi from '@/api/statistics';
import defaultCover from '@/assets/default-cover.png';

let echartsModule = null;

export default {
  name: 'AnnualReport',
  data() {
    return {
      userId: null,
      year: new Date().getFullYear(),
      currentYear: new Date().getFullYear(),
      report: null,
      loading: false,
      error: null,
      defaultCover,
      charts: [],
    };
  },
  created() {
    let userBase = {};
    try { userBase = JSON.parse(localStorage.getItem('userBase') || '{}'); } catch (e) { /* ignore */ }
    this.userId = userBase.id ?? null;
    if (this.$route.query.year) {
      this.year = parseInt(this.$route.query.year, 10) || this.currentYear;
    }
    if (this.userId) {
      this.loadReport();
    }
  },
  beforeUnmount() {
    this.disposeCharts();
  },
  methods: {
    async loadEcharts() {
      if (!echartsModule) {
        echartsModule = await import('echarts');
      }
      return echartsModule;
    },
    async loadReport() {
      this.loading = true;
      this.error = null;
      this.disposeCharts();
      try {
        const res = await statisticsApi.getAnnualReport(this.userId, this.year);
        if (res.data?.passed && res.data.data) {
          this.report = res.data.data;
          await this.loadEcharts();
          this.$nextTick(() => {
            requestAnimationFrame(() => this.renderCharts());
          });
        } else {
          this.error = res.data?.message || '加载报告失败';
        }
      } catch (e) {
        this.error = '加载报告失败：' + e.message;
      } finally {
        this.loading = false;
      }
    },
    changeYear(delta) {
      const next = this.year + delta;
      if (next < 2024 || next > this.currentYear) return;
      this.year = next;
      this.loadReport();
    },
    formatDuration(seconds) {
      if (!seconds || seconds <= 0) return '0分钟';
      const hours = Math.floor(seconds / 3600);
      const minutes = Math.floor((seconds % 3600) / 60);
      if (hours > 0) return `${hours}小时${minutes}分钟`;
      return `${minutes}分钟`;
    },
    goToSong(songId) {
      if (songId) this.$router.push(`/song/${songId}`);
    },
    disposeCharts() {
      this.charts.forEach(c => { try { c.dispose(); } catch (_) { /* ignore */ } });
      this.charts = [];
    },
    initChart(refName) {
      const echarts = echartsModule;
      if (!echarts) return null;
      const el = this.$refs[refName];
      if (!el || el.clientWidth === 0) return null;
      const chart = echarts.init(el);
      this.charts.push(chart);
      return chart;
    },
    renderCharts() {
      try {
        this.renderMonthlyChart();
        this.renderHourlyChart();
        this.renderLanguageChart();
        this.renderMoodChart();
      } catch (e) {
        console.warn('图表渲染异常', e);
      }
    },
    renderMonthlyChart() {
      const echarts = echartsModule;
      const chart = this.initChart('monthlyChart');
      if (!chart || !echarts || !this.report.monthlyTrend) return;
      const months = this.report.monthlyTrend.map(m => m.month + '月');
      const values = this.report.monthlyTrend.map(m => m.playCount);
      chart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 40, right: 20, top: 20, bottom: 30 },
        xAxis: { type: 'category', data: months, axisLabel: { color: '#64748b' } },
        yAxis: { type: 'value', axisLabel: { color: '#64748b' }, splitLine: { lineStyle: { color: 'rgba(0,0,0,0.06)' } } },
        series: [{
          type: 'line',
          data: values,
          smooth: true,
          symbol: 'circle',
          symbolSize: 8,
          lineStyle: { width: 3, color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#667eea' },
            { offset: 1, color: '#764ba2' },
          ])},
          itemStyle: { color: '#667eea' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(102,126,234,0.3)' },
              { offset: 1, color: 'rgba(118,75,162,0.02)' },
            ]),
          },
        }],
      });
    },
    renderHourlyChart() {
      const echarts = echartsModule;
      const chart = this.initChart('hourlyChart');
      if (!chart || !echarts || !this.report.hourlyDistribution) return;
      const hours = this.report.hourlyDistribution.map((_, i) => i + '时');
      const values = this.report.hourlyDistribution;
      chart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 40, right: 20, top: 20, bottom: 30 },
        xAxis: { type: 'category', data: hours, axisLabel: { color: '#64748b', interval: 3 } },
        yAxis: { type: 'value', axisLabel: { color: '#64748b' }, splitLine: { lineStyle: { color: 'rgba(0,0,0,0.06)' } } },
        series: [{
          type: 'bar',
          data: values,
          barWidth: '60%',
          itemStyle: {
            borderRadius: [4, 4, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#4facfe' },
              { offset: 1, color: '#00f2fe' },
            ]),
          },
        }],
      });
    },
    renderLanguageChart() {
      const echarts = echartsModule;
      const chart = this.initChart('languageChart');
      if (!chart || !echarts || !this.report.languageDistribution) return;
      const data = this.report.languageDistribution.map(item => ({
        name: item.language,
        value: item.songCount,
      }));
      chart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} 首 ({d}%)' },
        series: [{
          type: 'pie',
          radius: ['40%', '70%'],
          center: ['50%', '50%'],
          avoidLabelOverlap: true,
          itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
          label: { show: true, color: '#334155' },
          data,
          color: ['#667eea', '#f093fb', '#4facfe', '#fda085', '#a8edea'],
        }],
      });
    },
    renderMoodChart() {
      const echarts = echartsModule;
      const chart = this.initChart('moodChart');
      if (!chart || !echarts || !this.report.moodDistribution) return;
      const moodLabels = {
        Calm: '平静', Energetic: '活力', Melancholic: '忧郁',
        Joyful: '欢快', Tense: '紧张', Romantic: '浪漫',
      };
      const indicators = Object.keys(this.report.moodDistribution).map(k => ({
        name: moodLabels[k] || k, max: 1,
      }));
      const values = Object.values(this.report.moodDistribution);
      chart.setOption({
        radar: {
          indicator: indicators,
          shape: 'polygon',
          splitNumber: 4,
          axisName: { color: '#475569', fontSize: 12 },
          splitLine: { lineStyle: { color: 'rgba(0,0,0,0.08)' } },
          splitArea: { areaStyle: { color: ['rgba(102,126,234,0.02)', 'rgba(102,126,234,0.05)'] } },
        },
        series: [{
          type: 'radar',
          data: [{ value: values, name: '心情分布' }],
          lineStyle: { color: '#764ba2', width: 2 },
          itemStyle: { color: '#764ba2' },
          areaStyle: { color: 'rgba(118,75,162,0.2)' },
        }],
      });
    },
  },
};
</script>

<style scoped>
.annual-report-container {
  max-width: 960px;
  margin: 0 auto;
  padding: 0 16px 60px;
}

.empty-tip {
  text-align: center;
  color: #64748b;
  padding: 80px 0;
  font-size: 16px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120px 0;
  color: #475569;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid rgba(102, 126, 234, 0.2);
  border-top-color: #667eea;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.hero-section {
  position: relative;
  border-radius: 24px;
  overflow: hidden;
  margin-bottom: 32px;
  padding: 48px 32px;
  text-align: center;
  color: #fff;
}

.hero-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 50%, #f093fb 100%);
  z-index: 0;
}

.hero-inner {
  position: relative;
  z-index: 1;
}

.year-selector {
  display: inline-flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.year-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.5);
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.year-btn:hover:not(.disabled) {
  background: rgba(255, 255, 255, 0.3);
  border-color: #fff;
}

.year-btn.disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.year-display {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 2px;
}

.hero-title {
  font-size: 32px;
  font-weight: 800;
  margin: 0 0 8px;
}

.hero-subtitle {
  font-size: 18px;
  opacity: 0.9;
  margin: 0;
}

.replay-btn {
  margin-top: 16px;
  padding: 10px 24px;
  border-radius: 999px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  backdrop-filter: blur(4px);
}

.replay-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  border-color: #fff;
  transform: translateY(-2px);
}

.stats-overview {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 16px;
  margin-bottom: 32px;
}

.stat-card {
  background: #fff;
  border-radius: 16px;
  padding: 24px 16px;
  text-align: center;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
  transition: transform 0.2s;
}

.stat-card:hover {
  transform: translateY(-3px);
}

.stat-icon {
  font-size: 28px;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
}

.stat-plays { border-top: 3px solid #667eea; }
.stat-duration { border-top: 3px solid #f093fb; }
.stat-songs { border-top: 3px solid #4facfe; }
.stat-streak { border-top: 3px solid #fda085; }
.stat-completion { border-top: 3px solid #a8edea; }

.section-title {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 16px;
}

.favorite-singer-section {
  margin-bottom: 32px;
}

.favorite-singer-card {
  display: flex;
  align-items: center;
  gap: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  padding: 24px;
  color: #fff;
  box-shadow: 0 12px 28px rgba(102, 126, 234, 0.25);
}

.singer-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid rgba(255, 255, 255, 0.4);
}

.singer-details h3 {
  margin: 0 0 4px;
  font-size: 22px;
}

.singer-details p {
  margin: 0;
  opacity: 0.85;
  font-size: 14px;
}

.top-songs-section,
.top-singers-section {
  margin-bottom: 32px;
}

.top-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.top-item {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 12px;
  padding: 12px 16px;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.06);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}

.top-item:hover {
  transform: translateX(4px);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.1);
}

.top-rank {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  color: #fff;
  background: #94a3b8;
  flex-shrink: 0;
}

.rank-1 { background: linear-gradient(135deg, #fbbf24, #f59e0b); }
.rank-2 { background: linear-gradient(135deg, #94a3b8, #64748b); }
.rank-3 { background: linear-gradient(135deg, #d97706, #b45309); }

.top-cover {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}

.top-info {
  flex: 1;
  min-width: 0;
}

.top-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.top-meta {
  margin: 2px 0 0;
  font-size: 13px;
  color: #64748b;
}

.singers-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 14px;
}

.singer-card {
  background: #fff;
  border-radius: 14px;
  padding: 20px 12px;
  text-align: center;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.06);
  position: relative;
  transition: transform 0.2s;
}

.singer-card:hover {
  transform: translateY(-3px);
}

.singer-rank {
  position: absolute;
  top: 10px;
  left: 10px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.singer-card-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
  margin-bottom: 8px;
  border: 2px solid rgba(102, 126, 234, 0.2);
}

.singer-card-name {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.singer-card-meta {
  margin: 0;
  font-size: 12px;
  color: #64748b;
}

.charts-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.chart-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
}

.chart-panel {
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
}

.chart-title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: #334155;
}

.chart-canvas {
  width: 100%;
  height: 280px;
}
</style>
