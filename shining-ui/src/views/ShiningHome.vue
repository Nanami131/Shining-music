<template>
  <div class="discover-page">
    <section class="hero-panel">
      <div class="hero-content">
        <p class="eyebrow">SHINING · DISCOVER</p>
        <h1>点燃耳机里最鲜明的色彩</h1>
        <p class="sub-title">
          参考站内真实曲库，构筑一场关于「发现 · 分享 · 共鸣」的多彩旅程。
          新灵感与热爱在此汇聚。
        </p>
        <div class="hero-actions">
          <button class="primary" @click="goTo('/songs')">去听歌</button>
          <button class="ghost" @click="goTo('/playlists')">逛歌单</button>
          <button class="glass" @click="goTo('/forum')">冲进讨论区</button>
        </div>
        <div class="hero-stats">
          <div class="stat-item" v-for="item in heroStats" :key="item.label">
            <p class="value">{{ item.value }}</p>
            <p class="label">{{ item.label }}</p>
          </div>
        </div>
      </div>
      <div class="hero-visual">
        <div class="orbit orbit-1"></div>
        <div class="orbit orbit-2"></div>
        <div class="pulse"></div>
        <div
          class="spark"
          v-for="spark in sparkPoints"
          :key="spark.id"
          :style="{ top: spark.top, left: spark.left, animationDelay: spark.delay }"
        ></div>
      </div>
    </section>

    <section class="featured-panel">
      <div class="panel-head">
        <div>
          <h2>今日探索 · 依托真实曲目虚构的场景歌单</h2>
          <p>内容灵感来自站内歌曲，例如 {{ songTitlesPreview }} 等。</p>
        </div>
        <button class="ghost" @click="goTo('/songs')">查看更多歌曲</button>
      </div>

      <div class="featured-grid">
        <article
          v-for="mix in featuredMixes"
          :key="mix.title"
          class="featured-card"
          :style="{ '--accent': mix.accent }"
        >
          <header>
            <p class="tag">{{ mix.tag }}</p>
            <span class="track-id">#{{ mix.highlightSong.id }}</span>
          </header>
          <h3>{{ mix.title }}</h3>
          <p>{{ mix.desc }}</p>
          <div class="badges">
            <span v-for="badge in mix.badges" :key="badge">{{ badge }}</span>
          </div>
          <div class="track-highlight">
            <p>推荐曲 · {{ mix.highlightSong.title }}</p>
            <button class="mini-btn" @click="goTo(mix.route)">立即播放</button>
          </div>
        </article>
      </div>
    </section>

    <section class="mood-panel">
      <div class="panel-head">
        <div>
          <h2>情绪光谱 · 把状态交给音乐</h2>
          <p>每个情绪卡片都配了来源于真实曲库的推荐曲。</p>
        </div>
        <button class="ghost" @click="goTo('/playlists')">更多情绪歌单</button>
      </div>
      <div class="mood-grid">
        <article
          class="mood-card"
          v-for="mood in moodSets"
          :key="mood.title"
        >
          <div class="mood-top">
            <span class="emoji">{{ mood.emoji }}</span>
            <span class="tag">{{ mood.tag }}</span>
          </div>
          <h3>{{ mood.title }}</h3>
          <p>{{ mood.desc }}</p>
          <ul>
            <li v-for="tip in mood.tips" :key="tip">{{ tip }}</li>
          </ul>
          <div class="recommend">
            <p>推荐曲：</p>
            <span
              class="chip"
              v-for="song in mood.songs"
              :key="song.id"
            >
              {{ song.title }}
            </span>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script>
const SONG_LIBRARY = [
  { id: 2, title: '嘘の火花' },
  { id: 3, title: '破月' },
  { id: 4, title: '【Ib】絵？あぁ、そう' },
  { id: 5, title: 'WAVE' },
  { id: 6, title: 'heart.beat' },
  { id: 7, title: '深海少女' },
  { id: 8, title: '嘘' },
  { id: 9, title: '夜に駆ける' },
  { id: 10, title: '群青' },
  { id: 11, title: '六兆年と一夜物語' },
  { id: 12, title: '「Q」&「A」' },
  { id: 13, title: '夜雀旋律之心' },
  { id: 14, title: '無意識レクイエム' },
  { id: 15, title: '人生進行形' },
  { id: 16, title: '童游' },
  { id: 17, title: 'ためすがめ' },
  { id: 18, title: 'しあわせのかたち' },
  { id: 19, title: 'サンライトフラワーデ' },
  { id: 20, title: 'Stella-rium' },
  { id: 21, title: 'missing promise' },
];

export default {
  name: 'ShiningHome',
  data() {
    return {
      heroStats: [
        { value: '3.8M+', label: '月播放量' },
        { value: '21', label: '新入曲库' },
        { value: '98%', label: '推荐匹配度' },
      ],
      featuredMixes: [
        {
          title: '破月夜行 · 霓虹电气篇',
          desc: '从《破月》到《夜に駆ける》，把城市霓虹揉进节拍里。',
          badges: ['Synthwave', '都市夜色', '重低鼓'],
          tag: 'LIVE NOW',
          accent: '#f472b6',
          route: '/songs',
          highlightSong: SONG_LIBRARY.find((s) => s.id === 3),
        },
        {
          title: '深海失重 · 透明呼吸',
          desc: '《深海少女》 的空灵人声衔接 《Stella-rium》，营造失重的水下质感。',
          badges: ['Dream Pop', '沉浸', '空灵人声'],
          tag: '编辑推荐',
          accent: '#38bdf8',
          route: '/songs',
          highlightSong: SONG_LIBRARY.find((s) => s.id === 7),
        },
        {
          title: '童游集市 · 童话旋律',
          desc: '以《童游》和《しあわせのかたち》衍生的原声合集，适合写字和发呆。',
          badges: ['原声', '治愈', '木吉他'],
          tag: '温柔上线',
          accent: '#facc15',
          route: '/songs',
          highlightSong: SONG_LIBRARY.find((s) => s.id === 16),
        },
      ],
      moodSets: [
        {
          title: '凌晨写稿',
          desc: '屏幕泛蓝的凌晨，键盘声和鼓点同步。',
          tag: 'Night Shift',
          emoji: '🌃',
          tips: ['低饱和电子律动', '轻人声样本', '120 BPM 左右'],
          songs: [9, 11, 20].map((id) => SONG_LIBRARY.find((s) => s.id === id)),
        },
        {
          title: '午后梦游',
          desc: '阳光柔焦到木质桌面，灵感慢慢酝酿。',
          tag: 'Lazy Noon',
          emoji: '🌤️',
          tips: ['Lo-fi hiphop', '口风琴点缀', '轻打击'],
          songs: [6, 18, 19].map((id) => SONG_LIBRARY.find((s) => s.id === id)),
        },
        {
          title: '黄昏疾驰',
          desc: '地铁窗外闪过的灯带，与耳机里的合成器共鸣。',
          tag: 'City Rush',
          emoji: '🚇',
          tips: ['Future Bass', '切分节奏', '厚重贝斯'],
          songs: [5, 10, 21].map((id) => SONG_LIBRARY.find((s) => s.id === id)),
        },
      ],
      sparkPoints: [
        { id: 1, top: '20%', left: '25%', delay: '0s' },
        { id: 2, top: '35%', left: '70%', delay: '1s' },
        { id: 3, top: '60%', left: '30%', delay: '2s' },
        { id: 4, top: '15%', left: '60%', delay: '0.5s' },
        { id: 5, top: '70%', left: '55%', delay: '1.2s' },
        { id: 6, top: '45%', left: '10%', delay: '2.4s' },
        { id: 7, top: '55%', left: '80%', delay: '1.7s' },
        { id: 8, top: '28%', left: '85%', delay: '2.8s' },
      ],
    };
  },
  computed: {
    songTitlesPreview() {
      return SONG_LIBRARY.slice(0, 4).map((s) => s.title).join('、');
    },
  },
  methods: {
    goTo(path) {
      this.$router.push(path);
    },
  },
};
</script>

<style scoped>
.discover-page {
  min-height: calc(100vh - 60px);
  padding: clamp(20px, 5vw, 48px);
  display: flex;
  flex-direction: column;
  gap: 32px;
  background:
    radial-gradient(circle at 12% 18%, rgba(59, 130, 246, 0.55), transparent 55%),
    radial-gradient(circle at 88% 12%, rgba(244, 63, 94, 0.45), transparent 45%),
    radial-gradient(circle at 40% 85%, rgba(236, 72, 153, 0.4), transparent 60%),
    linear-gradient(125deg, #010120, #140b3a 45%, #2b0c5a 70%, #421164 100%);
  color: #ecf2ff;
}

.hero-panel {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 28px;
  padding: clamp(20px, 4vw, 40px);
  border-radius: 32px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.25), rgba(236, 72, 153, 0.3));
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 30px 75px rgba(2, 6, 23, 0.7);
}

.hero-content h1 {
  font-size: clamp(32px, 5vw, 54px);
  line-height: 1.2;
  margin: 12px 0 16px;
}

.eyebrow {
  letter-spacing: 0.3em;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.75);
}

.sub-title {
  color: rgba(236, 242, 255, 0.85);
  max-width: 520px;
}

.hero-actions {
  margin-top: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.primary,
.ghost,
.glass {
  border: none;
  border-radius: 999px;
  padding: 10px 24px;
  font-size: 15px;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.primary {
  background: linear-gradient(120deg, #38bdf8, #a855f7);
  color: #050014;
  box-shadow: 0 12px 35px rgba(168, 85, 247, 0.4);
}

.ghost {
  background: transparent;
  color: #f0f5ff;
  border: 1px solid rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(6px);
}

.glass {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 10px 25px rgba(15, 23, 42, 0.35);
}

.primary:hover,
.ghost:hover,
.glass:hover {
  transform: translateY(-2px);
}

.hero-stats {
  margin-top: 26px;
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
}

.stat-item {
  min-width: 120px;
}

.value {
  font-size: 30px;
  font-weight: 600;
}

.label {
  font-size: 12px;
  letter-spacing: 0.08em;
  color: rgba(255, 255, 255, 0.75);
}

.hero-visual {
  position: relative;
  min-height: 280px;
  overflow: hidden;
  border-radius: 28px;
  background: radial-gradient(circle, rgba(248, 250, 255, 0.2), transparent 70%);
}

.orbit {
  position: absolute;
  inset: 15%;
  border-radius: 50%;
  border: 1px dashed rgba(255, 255, 255, 0.35);
}

.orbit-1 {
  animation: rotate 16s linear infinite;
}

.orbit-2 {
  inset: 5%;
  border-style: solid;
  border-color: rgba(147, 197, 253, 0.35);
  animation: rotate 24s linear infinite reverse;
}

.pulse {
  position: absolute;
  inset: 25%;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.45), transparent 65%);
  animation: pulse 6s ease-in-out infinite;
}

.spark {
  position: absolute;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: rgba(248, 250, 255, 0.85);
  box-shadow: 0 0 12px rgba(248, 250, 255, 0.8);
  animation: drift 12s linear infinite;
}

.spark:nth-child(odd) {
  animation-duration: 15s;
}

@keyframes rotate {
  to {
    transform: rotate(360deg);
  }
}

@keyframes pulse {
  0% {
    transform: scale(0.9);
    opacity: 0.6;
  }
  50% {
    transform: scale(1.1);
    opacity: 1;
  }
  100% {
    transform: scale(0.9);
    opacity: 0.6;
  }
}

@keyframes drift {
  from {
    transform: translate3d(0, 0, 0) scale(0.8);
  }
  to {
    transform: translate3d(40px, -60px, 0) scale(1.4);
  }
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}

.featured-panel,
.mood-panel {
  padding: clamp(20px, 4vw, 28px);
  border-radius: 28px;
  background: rgba(10, 12, 35, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 25px 55px rgba(1, 2, 23, 0.6);
}

.featured-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
}

.featured-card {
  padding: 20px;
  border-radius: 24px;
  background: rgba(1, 3, 20, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.05);
  position: relative;
  overflow: hidden;
}

.featured-card::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  border: 1px solid transparent;
  background: linear-gradient(120deg, transparent, var(--accent), transparent);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.featured-card:hover::after {
  opacity: 0.45;
}

.featured-card header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
  margin-bottom: 8px;
}

.tag {
  letter-spacing: 0.2em;
}

.track-id {
  font-family: 'JetBrains Mono', monospace;
  color: var(--accent);
}

.badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}

.badges span {
  padding: 5px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  font-size: 12px;
}

.track-highlight {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.mini-btn {
  padding: 6px 16px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  border: none;
  color: #fff;
  cursor: pointer;
}

.mini-btn:hover {
  background: rgba(255, 255, 255, 0.25);
}

.mood-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 18px;
}

.mood-card {
  padding: 18px;
  border-radius: 22px;
  background: linear-gradient(140deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.02));
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.mood-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.emoji {
  font-size: 28px;
}

.mood-card ul {
  margin: 10px 0;
  padding-left: 18px;
  color: rgba(236, 242, 255, 0.85);
}

.recommend {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.chip {
  padding: 4px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.15);
  font-size: 12px;
}

@media (max-width: 768px) {
  .hero-stats {
    flex-direction: column;
  }
}
</style>
