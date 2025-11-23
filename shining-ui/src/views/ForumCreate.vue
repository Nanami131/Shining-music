<template>
  <div class="create-shell">
    <section class="create-panel">
      <div class="panel-head">
        <div>
          <p class="eyebrow">SHINING · POST</p>
          <h1>写下此刻的音乐心情</h1>
          <p class="desc">
            独立发帖页面，避免在列表页干扰浏览。内容会自动同步到讨论区。
          </p>
        </div>
        <button class="ghost" @click="goBack">返回讨论区</button>
      </div>

      <div v-if="!userId" class="login-tip">
        <p>登录后才可以发布帖子。</p>
        <button class="primary" @click="$router.push('/login')">前往登录</button>
      </div>

      <div v-else class="form-card">
        <label class="field">
          <span>标题</span>
          <input
            v-model="newTitle"
            type="text"
            placeholder="例如：通勤耳机里循环的 3 首歌"
          />
        </label>
        <label class="field">
          <span>正文</span>
          <textarea
            v-model="newContent"
            placeholder="写下你的故事、歌单或设备心得..."
          ></textarea>
        </label>
        <div class="form-foot">
          <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
          <button class="primary" :disabled="creating" @click="handleCreatePost">
            {{ creating ? '发布中...' : '发布帖子' }}
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import communityApi from '@/api/community';

export default {
  name: 'ForumCreate',
  data() {
    return {
      userId: null,
      newTitle: '',
      newContent: '',
      creating: false,
      errorMessage: '',
    };
  },
  created() {
    this.loadUser();
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
          this.$router.push('/forum');
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
    goBack() {
      this.$router.push('/forum');
    },
  },
};
</script>

<style scoped>
.create-shell {
  min-height: calc(100vh - 60px);
  padding: clamp(24px, 6vw, 60px);
  background:
    radial-gradient(circle at 10% 20%, rgba(59, 130, 246, 0.5), transparent 50%),
    radial-gradient(circle at 85% 15%, rgba(244, 63, 94, 0.45), transparent 55%),
    linear-gradient(135deg, #01010f, #140a35 50%, #2b0f50);
  display: flex;
  justify-content: center;
  align-items: flex-start;
  color: #f2f5ff;
}

.create-panel {
  width: min(840px, 100%);
  border-radius: 36px;
  padding: clamp(24px, 4vw, 40px);
  background: rgba(4, 7, 28, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 30px 60px rgba(2, 4, 20, 0.65);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.eyebrow {
  letter-spacing: 0.3em;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}

.panel-head h1 {
  font-size: clamp(28px, 4vw, 44px);
  margin: 10px 0;
}

.desc {
  color: rgba(242, 245, 255, 0.78);
  line-height: 1.7;
}

.ghost,
.primary {
  border: none;
  border-radius: 999px;
  padding: 10px 24px;
  font-size: 15px;
  cursor: pointer;
  transition: transform 0.2s ease;
}

.ghost {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.35);
  color: #f2f5ff;
}

.primary {
  background: linear-gradient(120deg, #38bdf8, #a855f7);
  color: #050014;
  box-shadow: 0 12px 30px rgba(168, 85, 247, 0.35);
}

.ghost:hover,
.primary:hover {
  transform: translateY(-2px);
}

.login-tip {
  margin-top: 36px;
  padding: 30px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.05);
  text-align: center;
}

.form-card {
  margin-top: 32px;
  padding: 24px;
  border-radius: 32px;
  background: rgba(1, 2, 20, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field span {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.75);
}

input,
textarea {
  width: 100%;
  border-radius: 18px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.05);
  color: #fff;
  padding: 12px 14px;
  font-size: 15px;
}

textarea {
  min-height: 180px;
  resize: vertical;
}

input:focus,
textarea:focus {
  outline: none;
  border-color: rgba(56, 189, 248, 0.85);
  box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.25);
}

.form-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.error {
  color: #fb7185;
  font-size: 14px;
}

.primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}
</style>
