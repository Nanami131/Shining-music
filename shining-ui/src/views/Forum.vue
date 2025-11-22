<template>
  <div class="forum-container">
    <h2 class="forum-title">讨论区</h2>

    <div v-if="!userId" class="forum-tip">
      <p>请先登录后再发帖。</p>
    </div>

    <div v-else class="post-create-card">
      <h3>发布新帖</h3>
      <input
          v-model="newTitle"
          class="input"
          placeholder="请输入标题"
      />
      <textarea
          v-model="newContent"
          class="textarea"
          placeholder="请输入正文"
      ></textarea>
      <button
          class="btn"
          :disabled="creating"
          @click="handleCreatePost"
      >
        {{ creating ? '发布中...' : '发布' }}
      </button>
      <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
    </div>

    <div class="post-list">
      <h3>最新帖子</h3>
      <div v-if="loading">加载中...</div>
      <div v-else-if="posts.length === 0">暂时还没有帖子，来抢个沙发吧~</div>
      <div
          v-else
      >
        <div
            v-for="post in posts"
            :key="post.id"
            class="post-item"
            @click="goDetail(post.id)"
        >
          <div class="post-title-line">
            <span class="post-title">{{ post.title }}</span>
          </div>
          <div class="post-meta">
            <span>评论数：{{ post.commentCount ?? 0 }}</span>
            <span>创建时间：{{ formatDate(post.createdAt) }}</span>
          </div>
        </div>
      </div>
    </div>
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
      newTitle: '',
      newContent: '',
      creating: false,
      errorMessage: '',
    };
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
    async handleCreatePost() {
      this.errorMessage = '';
      if (!this.userId) {
        this.errorMessage = '请先登录';
        return;
      }
      if (!this.newTitle.trim() || !this.newContent.trim()) {
        this.errorMessage = '标题和正文不能为空';
        return;
      }
      this.creating = true;
      try {
        const payload = {
          userId: this.userId,
          title: this.newTitle.trim(),
          content: this.newContent.trim(),
        };
        const res = await communityApi.createPost(payload);
        if (res && res.data && res.data.passed) {
          this.newTitle = '';
          this.newContent = '';
          await this.loadPosts();
        } else {
          const msg = res && res.data ? res.data.message : '发布失败';
          this.errorMessage = msg;
        }
      } catch (e) {
        this.errorMessage = '发布失败，请稍后重试';
      } finally {
        this.creating = false;
      }
    },
    goDetail(id) {
      this.$router.push({ name: 'post-detail', params: { id } });
    },
  },
};
</script>

<style scoped>
.forum-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px 16px;
}

.forum-title {
  font-size: 24px;
  margin-bottom: 16px;
}

.forum-tip {
  padding: 12px;
  margin-bottom: 16px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.08);
}

.post-create-card {
  padding: 16px;
  margin-bottom: 24px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(8px);
}

.input,
.textarea {
  width: 100%;
  box-sizing: border-box;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: rgba(0, 0, 0, 0.2);
  color: #fff;
  padding: 8px 10px;
  margin-top: 8px;
  margin-bottom: 8px;
}

.textarea {
  min-height: 100px;
  resize: vertical;
}

.btn {
  padding: 8px 16px;
  border-radius: 8px;
  border: none;
  cursor: pointer;
  background: linear-gradient(135deg, #ff9a9e, #fad0c4);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error {
  margin-top: 8px;
  color: #ff6b6b;
}

.post-list h3 {
  margin-bottom: 12px;
}

.post-item {
  padding: 12px 14px;
  margin-bottom: 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.04);
  cursor: pointer;
  transition: background 0.2s ease;
}

.post-item:hover {
  background: rgba(255, 255, 255, 0.08);
}

.post-title-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.post-title {
  font-weight: 600;
}

.post-meta {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.8;
  display: flex;
  gap: 12px;
}
</style>
