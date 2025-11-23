<template>
  <div class="forum-shell">
    <section class="forum-hero">
      <div class="hero-text">
        <p class="eyebrow">SHINING · COMMUNITY</p>
        <h1>点燃讨论区的霓虹浪潮</h1>
        <p class="hero-desc">
          不再在首页堆放编辑器，发帖将跳转至独立页面。
          你只要负责分享音乐故事，其余交给 Shining。
        </p>
        <div class="hero-actions">
          <button class="primary" @click="goCreatePage">
            立即发帖
          </button>
          <button class="ghost" @click="loadPosts" :disabled="loading">
            {{ loading ? '刷新中...' : '刷新列表' }}
          </button>
        </div>
        <div class="hero-stats">
          <div class="stat" v-for="stat in heroStats" :key="stat.label">
            <div class="value">{{ stat.value }}</div>
            <div class="label">{{ stat.label }}</div>
          </div>
        </div>
      </div>
      <div class="hero-visual">
        <div class="wave wave-1"></div>
        <div class="wave wave-2"></div>
        <div class="wave wave-3"></div>
        <div class="glow"></div>
      </div>
    </section>

    <section class="forum-body">
      <aside class="info-column">
        <article class="info-card">
          <h3>本周热听轨迹</h3>
          <p class="info-desc">真实曲库里讨论最多的作品，从这些歌开始热身：</p>
          <div class="song-list">
            <div class="song-item" v-for="song in focusSongs" :key="song.id">
              <div class="song-text">
                <p class="song-title">{{ song.title }}</p>
                <p class="song-meta">{{ song.mood }}</p>
              </div>
              <p class="song-desc">{{ song.desc }}</p>
            </div>
          </div>
        </article>

        <article class="rule-card">
          <h3>讨论小贴士</h3>
          <ul>
            <li>引用歌曲或歌单时，尽量附上曲名和 ID（示例：夜に駆ける #9）。</li>
            <li>演出现场 or 设备党点评，欢迎添加图片链接（社区自动缓存）。</li>
            <li>如果想提问，先搜索是否已有相同讨论，保持讨论干净有序。</li>
          </ul>
        </article>

        <article v-if="!userId" class="login-card">
          <h3>尚未登录</h3>
          <p>登录后才可以进入发帖页参与讨论。</p>
          <button class="primary" @click="$router.push('/login')">去登录</button>
        </article>
      </aside>

      <div class="post-column">
        <header class="post-header">
          <div>
            <p class="post-eyebrow">LIVE FEED</p>
            <h2>最新帖子</h2>
            <p class="post-sub">音乐故事、演出速报、设备讨论都在这里实时更新。</p>
          </div>
          <button class="glass" @click="goCreatePage">
            写一篇
          </button>
        </header>

        <div v-if="loading" class="state-card">加载中，稍等一下下...</div>
        <div v-else-if="posts.length === 0" class="state-card">
          暂无帖子，成为第一个点亮讨论区的人吧。
        </div>
        <div v-else class="post-grid">
          <article class="post-card" v-for="post in posts" :key="post.id">
            <div class="post-card-head">
              <h3>{{ post.title }}</h3>
              <span class="comment-count">{{ post.commentCount ?? 0 }} 评论</span>
            </div>
            <p class="excerpt">{{ makeExcerpt(post.content) }}</p>
            <div class="post-meta">
              <span>{{ formatDate(post.createdAt) }}</span>
              <button class="mini-btn" @click="goDetail(post.id)">查看详情</button>
            </div>
          </article>
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import communityApi from '@/api/community';

export default {
  name: 'Forum',
  data() {
    return {
      userId: null,
      posts: [],
      loading: false,
      focusSongs: [
        {
          id: 9,
          title: '夜に駆ける',
          mood: '高速都市夜行',
          desc: '在夜跑和午夜复盘贴里被提及最多的 Vapor 成员。',
        },
        {
          id: 7,
          title: '深海少女',
          mood: '沉浸系电子',
          desc: '关于「失重」和「透明感」的讨论从未间断。',
        },
        {
          id: 16,
          title: '童游',
          mood: '原声治愈',
          desc: '和写作、绘画、手账有关的帖子最爱引用这首歌。',
        },
        {
          id: 20,
          title: 'Stella-rium',
          mood: '星际浪漫',
          desc: '演出回顾与合唱翻唱区的常驻曲目。',
        },
      ],
    };
  },
  computed: {
    heroStats() {
      return [
        { value: `${this.posts.length || 0}`, label: '实时帖子' },
        { value: '82%', label: '回应率' },
        { value: '120+', label: '今日上线乐迷' },
      ];
    },
  },
  created() {
    this.loadUser();
    this.loadPosts();
  },
  methods: {
    loadUser() {
      try {
        const raw = localStorage.getItem('userBase') || '{}';
        const userBase = JSON.parse(raw);
        const id = userBase && userBase.id;
        this.userId = id != null ? id : null;
      } catch (e) {
        this.userId = null;
      }
    },
    async loadPosts() {
      this.loading = true;
      try {
        const res = await communityApi.listPosts();
        if (res && res.data && res.data.passed) {
          this.posts = res.data.data || [];
        } else {
          const msg = res && res.data ? res.data.message : '未知错误';
          alert('获取帖子列表失败：' + msg);
        }
      } catch (e) {
        alert('获取帖子列表失败：' + e.message);
      } finally {
        this.loading = false;
      }
    },
    formatDate(val) {
      if (!val) return '';
      try {
        return new Date(val).toLocaleString();
      } catch {
        return String(val);
      }
    },
    makeExcerpt(content) {
      if (!content) return '还没有正文描述，点击查看详情。';
      const clean = String(content).replace(/\s+/g, ' ').trim();
      return clean.length > 90 ? `${clean.slice(0, 90)}…` : clean;
    },
    goDetail(id) {
      this.$router.push({ name: 'post-detail', params: { id } });
    },
    goCreatePage() {
      if (!this.userId) {
        this.$router.push('/login');
        return;
      }
      this.$router.push({ name: 'forum-create' });
    },
  },
};
</script>

<style scoped>
.forum-shell {
  min-height: calc(100vh - 60px);
  padding: clamp(20px, 5vw, 48px);
  display: flex;
  flex-direction: column;
  gap: 32px;
  background:
    radial-gradient(circle at 10% 20%, rgba(56, 189, 248, 0.45), transparent 45%),
    radial-gradient(circle at 85% 10%, rgba(244, 63, 94, 0.4), transparent 50%),
    radial-gradient(circle at 40% 90%, rgba(129, 140, 248, 0.35), transparent 55%),
    linear-gradient(150deg, #020617 0%, #090f2b 40%, #1f0f3a 80%);
  color: #e4ebff;
}

.forum-hero {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 28px;
  padding: clamp(20px, 4vw, 36px);
  border-radius: 32px;
  background: linear-gradient(125deg, rgba(59, 130, 246, 0.3), rgba(236, 72, 153, 0.35));
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 35px 70px rgba(2, 6, 23, 0.7);
}

.eyebrow {
  letter-spacing: 0.28em;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}

.hero-text h1 {
  font-size: clamp(30px, 4.5vw, 52px);
  margin: 12px 0;
  line-height: 1.3;
}

.hero-desc {
  color: rgba(228, 235, 255, 0.85);
  line-height: 1.7;
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
  box-shadow: 0 15px 40px rgba(56, 189, 248, 0.45);
}

.ghost {
  background: transparent;
  color: #f0f5ff;
  border: 1px solid rgba(255, 255, 255, 0.35);
}

.glass {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 12px 30px rgba(4, 6, 25, 0.4);
}

.primary:disabled,
.ghost:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.hero-stats {
  margin-top: 28px;
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
}

.stat {
  min-width: 120px;
}

.stat .value {
  font-size: 28px;
  font-weight: 600;
}

.stat .label {
  font-size: 12px;
  letter-spacing: 0.1em;
  color: rgba(255, 255, 255, 0.75);
}

.hero-visual {
  position: relative;
  min-height: 260px;
  border-radius: 28px;
  overflow: hidden;
  background: rgba(4, 7, 22, 0.35);
}

.wave {
  position: absolute;
  inset: 15%;
  border-radius: 50%;
  border: 1px dashed rgba(255, 255, 255, 0.35);
  animation: spin 12s linear infinite;
}

.wave-2 {
  inset: 8%;
  border-color: rgba(59, 130, 246, 0.45);
  animation-duration: 18s;
}

.wave-3 {
  inset: 25%;
  border-color: rgba(236, 72, 153, 0.4);
  animation-duration: 24s;
}

.glow {
  position: absolute;
  inset: 20%;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.25), transparent 70%);
  filter: blur(6px);
  animation: pulse 8s ease-in-out infinite;
}

@keyframes spin {
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
    transform: scale(1.15);
    opacity: 1;
  }
  100% {
    transform: scale(0.9);
    opacity: 0.6;
  }
}

.forum-body {
  display: grid;
  grid-template-columns: minmax(260px, 340px) minmax(0, 1fr);
  gap: 28px;
  align-items: start;
}

.info-column {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.info-card,
.rule-card,
.login-card,
.post-column {
  padding: 20px;
  border-radius: 26px;
  background: rgba(6, 9, 28, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow: 0 25px 50px rgba(1, 2, 15, 0.6);
}

.post-column {
  padding: clamp(20px, 3vw, 28px);
}

.info-desc {
  color: rgba(228, 235, 255, 0.7);
  margin-bottom: 12px;
}

.song-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.song-item {
  padding: 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.04);
}

.song-title {
  font-weight: 600;
}

.song-meta {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
}

.song-desc {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
  margin-top: 6px;
}

.rule-card ul {
  margin-top: 8px;
  padding-left: 18px;
  color: rgba(228, 235, 255, 0.75);
  line-height: 1.6;
}

.login-card {
  text-align: center;
}

.post-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.post-eyebrow {
  letter-spacing: 0.2em;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
}

.post-sub {
  color: rgba(228, 235, 255, 0.7);
  margin-top: 4px;
}

.state-card {
  margin-top: 20px;
  padding: 28px;
  border-radius: 20px;
  background: rgba(1, 4, 20, 0.8);
  text-align: center;
  color: rgba(228, 235, 255, 0.8);
}

.post-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
  margin-top: 24px;
}

.post-card {
  padding: 18px;
  border-radius: 22px;
  background: rgba(2, 5, 18, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.05);
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.post-card:hover {
  transform: translateY(-4px);
  border-color: rgba(56, 189, 248, 0.5);
}

.post-card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.comment-count {
  font-size: 12px;
  color: rgba(236, 72, 153, 0.85);
}

.excerpt {
  color: rgba(228, 235, 255, 0.85);
  min-height: 50px;
}

.post-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.72);
}

.mini-btn {
  padding: 6px 14px;
  border-radius: 999px;
  border: none;
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  cursor: pointer;
}

.mini-btn:hover {
  background: rgba(255, 255, 255, 0.25);
}

@media (max-width: 960px) {
  .forum-body {
    grid-template-columns: 1fr;
  }
}
</style>
