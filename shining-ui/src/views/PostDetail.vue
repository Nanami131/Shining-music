<template>
  <div class="post-detail-container">
    <div v-if="loading" class="loading">正在加载帖子详情...</div>
    <div v-else-if="!post" class="empty">帖子不存在或已删除。</div>
    <div v-else>
      <h2 class="post-title">{{ post.title }}</h2>
      <div class="post-meta">
        <span>评论数：{{ post.commentCount ?? 0 }}</span>
        <span>创建时间：{{ formatDate(post.createdAt) }}</span>
        <span v-if="post.lastCommentAt">最后评论：{{ formatDate(post.lastCommentAt) }}</span>
      </div>
      <div class="post-content">{{ post.content }}</div>

      <section class="comment-section">
        <h3>评论</h3>

        <div v-if="!userId" class="comment-tip">
          请先登录后再发表评论。
        </div>

        <div v-else class="comment-create">
          <textarea
              v-model="newComment"
              class="textarea"
              placeholder="说点什么..."
          ></textarea>
          <button
              class="btn"
              :disabled="commenting"
              @click="handleCreateRootComment"
          >
            {{ commenting ? '发表中...' : '发表评论' }}
          </button>
          <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
        </div>

        <div class="comment-list">
          <div v-if="comments.length === 0" class="empty-comment">
            还没有评论，来抢沙发吧~
          </div>
          <div
              v-else
              v-for="c in comments"
              :key="c.id"
              class="comment-item"
          >
            <div class="comment-main">
              <div class="comment-header">
                <span class="user">用户 {{ c.userId ?? '未知' }}</span>
                <span class="time">{{ formatDate(c.createdAt) }}</span>
              </div>
              <div class="comment-content">{{ c.content }}</div>
            </div>

            <div class="comment-actions" v-if="userId">
              <button class="link-btn" @click="toggleReplyInput(c.id)">
                {{ showReplyFor === c.id ? '取消回复' : '回复' }}
              </button>
            </div>

            <div
                v-if="showReplyFor === c.id"
                class="reply-input"
            >
              <textarea
                  v-model="replyText"
                  class="textarea small"
                  placeholder="回复一下..."
              ></textarea>
              <button
                  class="btn small"
                  :disabled="commenting"
                  @click="handleCreateReply(c)"
              >
                {{ commenting ? '发送中...' : '发送回复' }}
              </button>
            </div>

            <div
                v-if="c.replies && c.replies.length > 0"
                class="reply-list"
            >
              <div
                  v-for="r in c.replies"
                  :key="r.id"
                  class="reply-item"
              >
                <div class="comment-header">
                  <span class="user">
                    用户 {{ r.userId ?? '未知' }}
                    <template v-if="r.replyToUserId">
                      &nbsp;@{{ r.replyToUserId }}
                    </template>
                  </span>
                  <span class="time">{{ formatDate(r.createdAt) }}</span>
                </div>
                <div class="comment-content">{{ r.content }}</div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script>
import communityApi from '@/api/community';

export default {
  name: 'PostDetail',
  data() {
    return {
      postId: null,
      post: null,
      comments: [],
      loading: false,
      userId: null,
      newComment: '',
      commenting: false,
      errorMessage: '',
      showReplyFor: null,
      replyText: '',
    };
  },
  created() {
    this.postId = Number(this.$route.params.id);
    this.loadUser();
    this.loadDetails();
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
    async loadDetails() {
      if (!this.postId) return;
      this.loading = true;
      try {
        const res = await communityApi.getPostDetails(this.postId);
        if (res && res.data && res.data.passed) {
          const data = res.data.data || {};
          this.post = {
            id: data.id,
            userId: data.userId,
            title: data.title,
            content: data.content,
            commentCount: data.commentCount,
            lastCommentAt: data.lastCommentAt,
            createdAt: data.createdAt,
          };
          // 后端返回的 comments 是扁平列表，按 parentId 组装成树
          this.comments = this.buildCommentTree(data.comments || []);
        } else {
          const msg = res && res.data ? res.data.message : '未知错误';
          alert('获取帖子详情失败：' + msg);
        }
      } catch (e) {
        alert('获取帖子详情失败：' + e.message);
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
    buildCommentTree(list) {
      if (!list || !list.length) {
        return [];
      }
      const map = {};
      list.forEach(item => {
        map[item.id] = { ...item, replies: [] };
      });
      const roots = [];
      list.forEach(item => {
        if (item.parentId) {
          const parent = map[item.parentId];
          if (parent) {
            parent.replies.push(map[item.id]);
          } else {
            roots.push(map[item.id]);
          }
        } else {
          roots.push(map[item.id]);
        }
      });
      // 按创建时间排序顶层评论
      roots.sort((a, b) => {
        if (!a.createdAt || !b.createdAt) return 0;
        return new Date(a.createdAt) - new Date(b.createdAt);
      });
      return roots;
    },
    async refreshComments() {
      // 单独刷新评论，不重新加载帖子
      if (!this.postId) return;
      try {
        const res = await communityApi.listComments(this.postId);
        if (res && res.data && res.data.passed) {
          this.comments = this.buildCommentTree(res.data.data || []);
        }
      } catch {
        // 忽略异常
      }
    },
    async handleCreateRootComment() {
      this.errorMessage = '';
      if (!this.userId) {
        this.errorMessage = '请先登录';
        return;
      }
      if (!this.newComment.trim()) {
        this.errorMessage = '评论内容不能为空';
        return;
      }
      this.commenting = true;
      try {
        const payload = {
          postId: this.postId,
          userId: this.userId,
          content: this.newComment.trim(),
        };
        const res = await communityApi.createComment(payload);
        if (res && res.data && res.data.passed) {
          this.newComment = '';
          this.showReplyFor = null;
          this.replyText = '';
          await this.refreshComments();
          await this.loadDetails();
        } else {
          const msg = res && res.data ? res.data.message : '评论失败';
          this.errorMessage = msg;
        }
      } catch (e) {
        this.errorMessage = '评论失败，请稍后重试';
      } finally {
        this.commenting = false;
      }
    },
    toggleReplyInput(id) {
      if (this.showReplyFor === id) {
        this.showReplyFor = null;
        this.replyText = '';
      } else {
        this.showReplyFor = id;
        this.replyText = '';
      }
    },
    async handleCreateReply(parent) {
      this.errorMessage = '';
      if (!this.userId) {
        this.errorMessage = '请先登录';
        return;
      }
      if (!this.replyText.trim()) {
        this.errorMessage = '回复内容不能为空';
        return;
      }
      this.commenting = true;
      try {
        const payload = {
          postId: this.postId,
          parentId: parent.id,
          userId: this.userId,
          replyToUserId: parent.userId,
          content: this.replyText.trim(),
        };
        const res = await communityApi.createComment(payload);
        if (res && res.data && res.data.passed) {
          this.showReplyFor = null;
          this.replyText = '';
          await this.refreshComments();
          await this.loadDetails();
        } else {
          const msg = res && res.data ? res.data.message : '回复失败';
          this.errorMessage = msg;
        }
      } catch (e) {
        this.errorMessage = '回复失败，请稍后重试';
      } finally {
        this.commenting = false;
      }
    },
  },
};
</script>

<style scoped>
.post-detail-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px 16px;
}

.loading,
.empty {
  margin-top: 40px;
  text-align: center;
  opacity: 0.8;
}

.post-title {
  font-size: 24px;
  margin-bottom: 8px;
}

.post-meta {
  font-size: 12px;
  opacity: 0.8;
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.post-content {
  padding: 16px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.04);
  margin-bottom: 24px;
  line-height: 1.6;
}

.comment-section h3 {
  margin-bottom: 12px;
}

.comment-tip {
  padding: 8px 10px;
  margin-bottom: 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.06);
}

.comment-create {
  margin-bottom: 16px;
}

.textarea {
  width: 100%;
  box-sizing: border-box;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: rgba(0, 0, 0, 0.2);
  color: #fff;
  padding: 8px 10px;
  margin-bottom: 8px;
  resize: vertical;
}

.textarea.small {
  min-height: 60px;
}

.btn {
  padding: 6px 12px;
  border-radius: 8px;
  border: none;
  cursor: pointer;
  background: linear-gradient(135deg, #a18cd1, #fbc2eb);
}

.btn.small {
  padding: 4px 10px;
  font-size: 12px;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error {
  margin-top: 4px;
  color: #ff6b6b;
  font-size: 12px;
}

.comment-list {
  margin-top: 8px;
}

.comment-item {
  padding: 10px 12px;
  margin-bottom: 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.04);
}

.comment-main {
  margin-bottom: 4px;
}

.comment-header {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  opacity: 0.85;
  margin-bottom: 4px;
}

.comment-content {
  font-size: 14px;
}

.comment-actions {
  margin-top: 4px;
}

.link-btn {
  border: none;
  background: transparent;
  color: #ffd6e0;
  cursor: pointer;
  font-size: 12px;
  padding: 0;
}

.reply-input {
  margin-top: 6px;
}

.reply-list {
  margin-top: 6px;
  padding-left: 12px;
  border-left: 1px solid rgba(255, 255, 255, 0.08);
}

.reply-item {
  margin-top: 6px;
}

.empty-comment {
  margin-top: 8px;
  font-size: 13px;
  opacity: 0.8;
}
</style>
