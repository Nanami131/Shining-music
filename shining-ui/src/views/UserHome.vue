<template>
  <div class="user-home-shell">
    <div v-if="loading" class="state-msg">加载中...</div>
    <div v-else-if="!userInfo" class="state-msg">用户不存在或已注销。</div>
    <template v-else>
      <!-- Banner -->
      <section class="banner">
        <div class="banner-bg"></div>
        <div class="banner-content">
          <div class="avatar-ring">
            <img :src="userInfo.avatarUrl || defaultAvatar" class="avatar" alt="头像" />
          </div>
          <div class="user-meta">
            <h1 class="nick">{{ userInfo.nickName || userInfo.username || '匿名用户' }}</h1>
            <p class="sig">{{ userInfo.signature || '这个人很神秘，还没有签名' }}</p>
            <div class="tags">
              <span class="tag" v-if="userInfo.username">@{{ userInfo.username }}</span>
              <span class="tag" v-if="userInfo.role === 1">管理员</span>
            </div>
          </div>
          <button v-if="isSelf" class="edit-btn" @click="$router.push('/profile')">编辑资料</button>
        </div>
      </section>

      <!-- Bio -->
      <section v-if="userInfo.profile" class="bio-section">
        <p class="bio-text">{{ userInfo.profile }}</p>
      </section>

      <!-- Tabs -->
      <section class="content-section">
        <div class="tab-bar">
          <span class="tab active">帖子 ({{ posts.length }})</span>
        </div>

        <div v-if="posts.length === 0" class="empty">
          {{ isSelf ? '你还没有发布任何帖子，去讨论区写一篇吧！' : '该用户暂无帖子。' }}
        </div>
        <div v-else class="post-grid">
          <article
            v-for="post in posts"
            :key="post.id"
            class="post-card"
            @click="goPostDetail(post.id)"
          >
            <h3>{{ post.title }}</h3>
            <p class="excerpt">{{ makeExcerpt(post.content) }}</p>
            <div class="post-foot">
              <span>{{ formatDate(post.createdAt) }}</span>
              <span>{{ post.commentCount ?? 0 }} 评论</span>
            </div>
          </article>
        </div>
      </section>
    </template>
  </div>
</template>

<script>
import userApi from '@/api/user';
import communityApi from '@/api/community';
import defaultAvatar from '@/assets/default-avatar.png';

export default {
  name: 'UserHome',
  data() {
    return {
      targetUserId: null,
      currentUserId: null,
      userInfo: null,
      posts: [],
      loading: false,
      defaultAvatar,
    };
  },
  computed: {
    isSelf() {
      return this.currentUserId != null && this.currentUserId === this.targetUserId;
    },
  },
  created() {
    this.targetUserId = Number(this.$route.params.id);
    try {
      const ub = JSON.parse(localStorage.getItem('userBase') || '{}');
      this.currentUserId = ub.id ?? null;
    } catch { this.currentUserId = null; }
    this.load();
  },
  watch: {
    '$route.params.id'(newId) {
      this.targetUserId = Number(newId);
      this.load();
    },
  },
  methods: {
    async load() {
      this.loading = true;
      try {
        const [userRes, postsRes] = await Promise.all([
          userApi.getUserDetailsInfo(this.targetUserId),
          communityApi.listPosts({ userId: this.targetUserId }),
        ]);
        this.userInfo = userRes?.data?.passed ? userRes.data.data : null;
        this.posts = postsRes?.data?.passed ? (postsRes.data.data || []) : [];
      } catch {
        this.userInfo = null;
        this.posts = [];
      } finally {
        this.loading = false;
      }
    },
    makeExcerpt(content) {
      if (!content) return '';
      const plain = String(content).replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
      return plain.length > 100 ? plain.slice(0, 100) + '…' : plain;
    },
    formatDate(val) {
      if (!val) return '';
      try { return new Date(val).toLocaleDateString(); } catch { return String(val); }
    },
    goPostDetail(id) {
      this.$router.push({ name: 'post-detail', params: { id } });
    },
  },
};
</script>

<style scoped>
.user-home-shell {
  min-height: calc(100vh - 60px);
  background:
    radial-gradient(circle at 15% 10%, rgba(56, 189, 248, 0.4), transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(168, 85, 247, 0.35), transparent 50%),
    linear-gradient(150deg, #020617, #0f0a2a 50%, #1a0e3a);
  color: #e4ebff;
  padding-bottom: 80px;
}

.state-msg {
  text-align: center;
  padding: 80px 20px;
  font-size: 16px;
  opacity: 0.7;
}

/* ---- Banner ---- */
.banner {
  position: relative;
  overflow: hidden;
  padding: clamp(40px, 8vw, 80px) clamp(20px, 5vw, 60px) clamp(28px, 4vw, 48px);
}

.banner-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 30% 50%, rgba(59, 130, 246, 0.35), transparent 60%),
    radial-gradient(ellipse at 80% 40%, rgba(236, 72, 153, 0.3), transparent 60%);
  filter: blur(40px);
  z-index: 0;
}

.banner-content {
  position: relative;
  z-index: 1;
  max-width: 900px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 28px;
  flex-wrap: wrap;
}

.avatar-ring {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  padding: 4px;
  background: linear-gradient(135deg, #38bdf8, #a855f7, #ec4899);
  flex-shrink: 0;
}

.avatar {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  display: block;
  border: 3px solid #0f172a;
}

.user-meta {
  flex: 1;
  min-width: 200px;
}

.nick {
  font-size: clamp(24px, 4vw, 38px);
  margin: 0 0 6px;
  font-weight: 700;
}

.sig {
  color: rgba(228, 235, 255, 0.7);
  font-size: 15px;
  margin: 0 0 10px;
}

.tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.tag {
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: rgba(228, 235, 255, 0.8);
}

.edit-btn {
  padding: 10px 24px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 999px;
  background: transparent;
  color: #f2f5ff;
  font-size: 14px;
  cursor: pointer;
  transition: transform 0.2s, background 0.2s;
  flex-shrink: 0;
}

.edit-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  transform: translateY(-2px);
}

/* ---- Bio ---- */
.bio-section {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 clamp(20px, 5vw, 60px) 20px;
}

.bio-text {
  padding: 16px 20px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  line-height: 1.7;
  color: rgba(228, 235, 255, 0.85);
  white-space: pre-wrap;
}

/* ---- Content ---- */
.content-section {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 clamp(20px, 5vw, 60px);
}

.tab-bar {
  display: flex;
  gap: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  margin-bottom: 24px;
}

.tab {
  padding: 10px 4px;
  font-size: 15px;
  color: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  position: relative;
}

.tab.active {
  color: #f2f5ff;
}

.tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: linear-gradient(90deg, #38bdf8, #a855f7);
  border-radius: 2px;
}

.empty {
  text-align: center;
  padding: 48px 20px;
  color: rgba(228, 235, 255, 0.6);
  font-size: 15px;
}

.post-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 18px;
}

.post-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(4, 7, 22, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.05);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: transform 0.2s, border-color 0.2s;
}

.post-card:hover {
  transform: translateY(-4px);
  border-color: rgba(56, 189, 248, 0.4);
}

.post-card h3 {
  font-size: 16px;
  margin: 0;
}

.excerpt {
  color: rgba(228, 235, 255, 0.7);
  font-size: 14px;
  flex: 1;
}

.post-foot {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: rgba(228, 235, 255, 0.5);
}
</style>
