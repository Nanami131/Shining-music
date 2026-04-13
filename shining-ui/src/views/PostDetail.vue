<template>
  <div class="post-detail-shell">
    <div v-if="loading" class="state-msg">
      <div class="spinner"></div>
      <span>加载中...</span>
    </div>
    <div v-else-if="!post" class="state-msg">帖子不存在或已删除。</div>
    <template v-else>
      <!-- Back -->
      <nav class="breadcrumb">
        <span class="back-link" @click="$router.back()">← 返回</span>
        <span class="sep">/</span>
        <span class="crumb" @click="$router.push('/forum')">社区</span>
      </nav>

      <!-- Post Card -->
      <article class="post-card">
        <h1 class="post-title">{{ post.title }}</h1>
        <div class="post-meta">
          <span class="author-link" @click="goUser(post.userId)">
            <img class="author-avatar" :src="avatarUrl(post.userId)" :alt="displayName(post.userId)" @error="handleAvatarError" />
            {{ displayName(post.userId) }}
          </span>
          <span class="meta-dot">·</span>
          <span>{{ formatDate(post.createdAt) }}</span>
          <span v-if="post.commentCount" class="meta-dot">·</span>
          <span v-if="post.commentCount">{{ post.commentCount }} 条评论</span>
        </div>
        <div class="post-body" v-html="sanitizedContent"></div>
        <div class="post-footer">
          <button class="like-btn" :class="{ liked: isLiked }" @click="handleLike">
            <span class="like-icon">{{ isLiked ? '❤️' : '🤍' }}</span>
            <span>{{ post.likeCount || 0 }}</span>
          </button>
          <span v-if="post.lastCommentAt" class="post-last-comment-inline">
            最后评论于 {{ formatDate(post.lastCommentAt) }}
          </span>
        </div>
      </article>

      <!-- Comment Section -->
      <section class="comment-section">
        <h2 class="section-title">
          <span class="title-icon">💬</span>
          评论 <span class="comment-count" v-if="comments.length">({{ comments.length }})</span>
        </h2>

        <!-- Comment Input -->
        <div v-if="!userId" class="login-tip">
          <span>请先登录后再发表评论。</span>
        </div>
        <div v-else class="comment-editor">
          <textarea
            v-model="newComment"
            class="editor-textarea"
            placeholder="写下你的想法..."
            rows="3"
          ></textarea>
          <div class="editor-footer">
            <p v-if="errorMessage" class="error-msg">{{ errorMessage }}</p>
            <button
              class="submit-btn"
              :disabled="commenting || !newComment.trim()"
              @click="handleCreateRootComment"
            >
              <span v-if="commenting" class="btn-spinner"></span>
              {{ commenting ? '发表中...' : '发表评论' }}
            </button>
          </div>
        </div>

        <!-- Comment List -->
        <div class="comment-list">
          <div v-if="comments.length === 0" class="empty-state">
            <span class="empty-icon">🫧</span>
            <p>还没有评论，来抢沙发吧~</p>
          </div>

          <div
            v-for="(c, idx) in comments"
            :key="c.id"
            class="comment-card"
          >
            <div class="comment-floor">#{{ c.floorNo || idx + 1 }}</div>
            <div class="comment-body">
              <div class="comment-head">
                <span class="commenter-link" @click.stop="goUser(c.userId)">
                  <img class="commenter-chip" :src="avatarUrl(c.userId)" :alt="displayName(c.userId)" @error="handleAvatarError" />
                  {{ displayName(c.userId) }}
                </span>
                <span class="comment-time">{{ formatDate(c.createdAt) }}</span>
              </div>
              <div class="comment-text">{{ c.content }}</div>
              <div class="comment-actions" v-if="userId">
                <button class="action-btn" @click="toggleReplyInput(c.id, c)">
                  {{ showReplyFor === c.id ? '取消' : '回复' }}
                </button>
              </div>

              <!-- Reply Input -->
              <div v-if="showReplyFor === c.id" class="reply-editor">
                <div v-if="replyTarget" class="reply-target-preview">
                  <span>正在回复 {{ displayName(replyTarget.userId) }}</span>
                  <p>{{ quoteText(replyTarget.content) }}</p>
                </div>
                <textarea
                  v-model="replyText"
                  class="editor-textarea small"
                  :placeholder="replyPlaceholder"
                  rows="2"
                ></textarea>
                <button
                  class="submit-btn small"
                  :disabled="commenting || !replyText.trim()"
                  @click="handleCreateReply(c)"
                >
                  {{ commenting ? '发送中...' : '发送' }}
                </button>
              </div>

              <!-- Replies -->
              <div v-if="c.replies && c.replies.length > 0" class="reply-thread">
                <div
                  v-for="r in c.replies"
                  :key="r.id"
                  class="reply-card"
                >
                  <div class="comment-head">
                    <span class="commenter-link" @click.stop="goUser(r.userId)">
                      <img class="commenter-chip reply-chip" :src="avatarUrl(r.userId)" :alt="displayName(r.userId)" @error="handleAvatarError" />
                      {{ displayName(r.userId) }}
                    </span>
                    <template v-if="r.replyToUserId">
                      <span class="reply-arrow">→</span>
                      <span class="commenter-link" @click.stop="goUser(r.replyToUserId)">@{{ displayName(r.replyToUserId) }}</span>
                    </template>
                    <span class="comment-time">{{ formatDate(r.createdAt) }}</span>
                  </div>
                  <div v-if="quotedComment(r)" class="reply-quote">
                    <span>引用 {{ displayName(quotedComment(r).userId) }}</span>
                    <p>{{ quoteText(quotedComment(r).content) }}</p>
                  </div>
                  <div class="comment-text reply-text">{{ r.content }}</div>
                  <div class="comment-actions" v-if="userId">
                    <button class="action-btn" @click="toggleReplyInput(r.id, r)">
                      {{ showReplyFor === r.id ? '取消' : '回复' }}
                    </button>
                  </div>
                  <div v-if="showReplyFor === r.id" class="reply-editor">
                    <div v-if="replyTarget" class="reply-target-preview">
                      <span>正在回复 {{ displayName(replyTarget.userId) }}</span>
                      <p>{{ quoteText(replyTarget.content) }}</p>
                    </div>
                    <textarea
                      v-model="replyText"
                      class="editor-textarea small"
                      :placeholder="replyPlaceholder"
                      rows="2"
                    ></textarea>
                    <button
                      class="submit-btn small"
                      :disabled="commenting || !replyText.trim()"
                      @click="handleCreateReply(r)"
                    >
                      {{ commenting ? '发送中...' : '发送' }}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script>
import communityApi from '@/api/community';
import userApi from '@/api/user';
import DOMPurify from 'dompurify';
import defaultAvatar from '@/assets/default-avatar.png';

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
      replyTarget: null,
      nickNameMap: {},
      avatarMap: {},
      commentMap: {},
      isLiked: false,
    };
  },
  created() {
    this.postId = Number(this.$route.params.id);
    this.loadUser();
    this.loadDetails();
    this.loadLikeStatus();
  },
  watch: {
    '$route.params.id'(newId) {
      this.postId = Number(newId);
      this.loadDetails();
    },
  },
  computed: {
    sanitizedContent() {
      if (!this.post || !this.post.content) return '';
      return DOMPurify.sanitize(this.post.content);
    },
    replyPlaceholder() {
      if (!this.replyTarget?.userId) return '回复一下...';
      return `回复 @${this.displayName(this.replyTarget.userId)}...`;
    },
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
            likeCount: data.likeCount || 0,
            lastCommentAt: data.lastCommentAt,
            createdAt: data.createdAt,
          };
          this.comments = this.normalizeComments(data.comments || []);
          await this.resolveNickNames();
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
    async resolveNickNames() {
      const ids = new Set();
      if (this.post?.userId) ids.add(this.post.userId);
      const collect = (list) => {
        for (const c of list || []) {
          if (c.userId) ids.add(c.userId);
          if (c.replyToUserId) ids.add(c.replyToUserId);
          if (c.replies) collect(c.replies);
        }
      };
      collect(this.comments);
      const toFetch = [...ids].filter(id => !(id in this.nickNameMap) || !(id in this.avatarMap));
      await Promise.all(toFetch.map(async id => {
        try {
          const res = await userApi.getUserBaseInfo(id);
          if (res.data?.passed && res.data.data) {
            this.nickNameMap[id] = res.data.data.nickName || res.data.data.username || `用户${id}`;
            this.avatarMap[id] = res.data.data.avatarUrl || '';
          } else {
            this.nickNameMap[id] = `用户${id}`;
            this.avatarMap[id] = '';
          }
        } catch {
          this.nickNameMap[id] = `用户${id}`;
          this.avatarMap[id] = '';
        }
      }));
    },
    displayName(uid) {
      return this.nickNameMap[uid] || `用户${uid}`;
    },
    avatarUrl(uid) {
      return this.avatarMap[uid] || defaultAvatar;
    },
    handleAvatarError(event) {
      event.target.src = defaultAvatar;
    },
    formatDate(val) {
      if (!val) return '';
      try {
        return new Date(val).toLocaleString();
      } catch {
        return String(val);
      }
    },
    normalizeComments(comments) {
      const commentMap = {};
      const record = (comment) => {
        if (!comment) return;
        commentMap[comment.id] = comment;
        for (const reply of comment.replies || []) {
          record(reply);
        }
      };
      for (const comment of comments || []) {
        record(comment);
      }
      this.commentMap = commentMap;

      const flattenReplies = (replies) => {
        const result = [];
        const visit = (reply) => {
          if (!reply) return;
          result.push({ ...reply, replies: [] });
          for (const child of reply.replies || []) {
            visit(child);
          }
        };
        for (const reply of replies || []) {
          visit(reply);
        }
        return result.sort((a, b) => {
          const aTime = a.createdAt ? new Date(a.createdAt).getTime() : 0;
          const bTime = b.createdAt ? new Date(b.createdAt).getTime() : 0;
          return aTime - bTime;
        });
      };

      return (comments || []).map(comment => ({
        ...comment,
        replies: flattenReplies(comment.replies),
      }));
    },
    quotedComment(comment) {
      if (!comment?.parentId) return null;
      const quoted = this.commentMap[comment.parentId];
      if (!quoted || quoted.id === comment.id) return null;
      return quoted;
    },
    quoteText(content) {
      const text = String(content || '').replace(/\s+/g, ' ').trim();
      return text.length > 80 ? `${text.slice(0, 80)}...` : text;
    },
    async refreshComments() {
      if (!this.postId) return;
      try {
        const res = await communityApi.listComments(this.postId);
        if (res && res.data && res.data.passed) {
          this.comments = this.normalizeComments(res.data.data || []);
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
          this.replyTarget = null;
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
    toggleReplyInput(id, target) {
      if (this.showReplyFor === id) {
        this.showReplyFor = null;
        this.replyText = '';
        this.replyTarget = null;
      } else {
        this.showReplyFor = id;
        this.replyText = '';
        this.replyTarget = target || null;
      }
    },
    async loadLikeStatus() {
      if (!this.postId) return;
      try {
        const res = await communityApi.getLikeStatus(this.postId);
        if (res.data?.passed) this.isLiked = res.data.data?.liked || false;
      } catch { /* silent */ }
    },
    async handleLike() {
      if (!this.userId) return;
      try {
        const res = await communityApi.toggleLike(this.postId);
        if (res.data?.passed) {
          this.isLiked = res.data.data?.liked || false;
          if (this.post) {
            this.post.likeCount = (this.post.likeCount || 0) + (this.isLiked ? 1 : -1);
            if (this.post.likeCount < 0) this.post.likeCount = 0;
          }
        }
      } catch { /* silent */ }
    },
    goUser(userId) {
      if (userId != null) {
        this.$router.push({ name: 'user-home', params: { id: userId } });
      }
    },
    async handleCreateReply(target) {
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
          parentId: target.id,
          userId: this.userId,
          replyToUserId: target.userId,
          content: this.replyText.trim(),
        };
        const res = await communityApi.createComment(payload);
        if (res && res.data && res.data.passed) {
          this.showReplyFor = null;
          this.replyText = '';
          this.replyTarget = null;
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
/* ======== Shell ======== */
.post-detail-shell {
  min-height: calc(100vh - 60px);
  background:
    radial-gradient(circle at 20% 10%, rgba(56, 189, 248, 0.35), transparent 50%),
    radial-gradient(circle at 75% 25%, rgba(168, 85, 247, 0.3), transparent 50%),
    linear-gradient(150deg, #020617, #0f0a2a 50%, #1a0e3a);
  color: #e4ebff;
  padding: 0 clamp(16px, 4vw, 48px) 80px;
  max-width: 860px;
  margin: 0 auto;
}

.state-msg {
  text-align: center;
  padding: 100px 20px;
  font-size: 15px;
  color: rgba(228, 235, 255, 0.6);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.spinner {
  width: 28px; height: 28px;
  border: 3px solid rgba(168, 85, 247, 0.2);
  border-top-color: #a855f7;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ======== Breadcrumb ======== */
.breadcrumb {
  padding: clamp(20px, 3vw, 36px) 0 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: rgba(228, 235, 255, 0.5);
}
.back-link, .crumb {
  cursor: pointer;
  transition: color 0.15s;
}
.back-link:hover, .crumb:hover { color: #93c5fd; }
.sep { opacity: 0.3; }

/* ======== Post Card ======== */
.post-card {
  padding: clamp(24px, 4vw, 40px);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
  backdrop-filter: blur(12px);
  margin-bottom: 32px;
}

.post-title {
  font-size: clamp(22px, 3.5vw, 32px);
  font-weight: 700;
  margin: 0 0 16px;
  line-height: 1.35;
  background: linear-gradient(135deg, #f2f5ff 60%, #a5b4fc);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.post-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: rgba(228, 235, 255, 0.55);
  margin-bottom: 24px;
  flex-wrap: wrap;
}
.meta-dot { opacity: 0.3; }

.author-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #93c5fd;
  font-weight: 600;
  transition: color 0.15s;
}
.author-link:hover { color: #60a5fa; }

.author-avatar {
  width: 26px; height: 26px;
  border-radius: 50%;
  background: linear-gradient(135deg, #38bdf8, #a855f7);
  display: block;
  object-fit: cover;
  flex-shrink: 0;
}

/* ======== Post Body (rich content) ======== */
.post-body {
  line-height: 1.85;
  word-break: break-word;
  font-size: 15px;
  color: rgba(228, 235, 255, 0.88);
}
.post-body :deep(img) {
  max-width: 100%;
  border-radius: 12px;
  margin: 12px 0;
}
.post-body :deep(a) {
  color: #93c5fd;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.post-body :deep(h1),
.post-body :deep(h2),
.post-body :deep(h3) {
  margin: 20px 0 10px;
  line-height: 1.4;
  color: #f2f5ff;
}
.post-body :deep(blockquote) {
  border-left: 3px solid rgba(168, 85, 247, 0.6);
  margin: 16px 0;
  padding: 10px 20px;
  color: rgba(228, 235, 255, 0.7);
  background: rgba(255, 255, 255, 0.03);
  border-radius: 0 12px 12px 0;
}
.post-body :deep(pre) {
  background: rgba(0, 0, 0, 0.5);
  border-radius: 12px;
  padding: 16px 20px;
  overflow-x: auto;
  margin: 16px 0;
  border: 1px solid rgba(255, 255, 255, 0.06);
}
.post-body :deep(code) {
  background: rgba(255, 255, 255, 0.08);
  color: #fbbf24;
  border-radius: 5px;
  padding: 2px 6px;
  font-size: 0.9em;
}
.post-body :deep(pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
}
.post-body :deep(ul),
.post-body :deep(ol) {
  padding-left: 24px;
  margin: 10px 0;
}
.post-body :deep(li) { margin: 5px 0; }
.post-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 16px 0;
}
.post-body :deep(th),
.post-body :deep(td) {
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 10px 14px;
  text-align: left;
}
.post-body :deep(th) {
  background: rgba(255, 255, 255, 0.06);
  font-weight: 600;
}
.post-body :deep(p) { margin: 10px 0; }

.post-footer {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  align-items: center;
  gap: 16px;
}
.like-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.04);
  color: rgba(228, 235, 255, 0.7);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}
.like-btn:hover { border-color: rgba(236, 72, 153, 0.4); background: rgba(236, 72, 153, 0.08); }
.like-btn.liked {
  border-color: rgba(236, 72, 153, 0.5);
  background: rgba(236, 72, 153, 0.12);
  color: #f9a8d4;
}
.like-icon { font-size: 16px; }
.post-last-comment-inline {
  font-size: 12px;
  color: rgba(228, 235, 255, 0.4);
  margin-left: auto;
}

/* ======== Comment Section ======== */
.comment-section {
  padding: clamp(24px, 3vw, 36px);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(8px);
}

.section-title {
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 20px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.title-icon { font-size: 20px; }
.comment-count {
  font-size: 14px;
  font-weight: 400;
  color: rgba(228, 235, 255, 0.5);
}

/* -- Login Tip -- */
.login-tip {
  padding: 14px 18px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 14px;
  color: rgba(228, 235, 255, 0.6);
  margin-bottom: 20px;
}

/* -- Editor -- */
.comment-editor, .reply-editor {
  margin-bottom: 24px;
}

.editor-textarea {
  width: 100%;
  box-sizing: border-box;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(0, 0, 0, 0.3);
  color: #e4ebff;
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
  transition: border-color 0.2s, box-shadow 0.2s;
  font-family: inherit;
}
.editor-textarea:focus {
  outline: none;
  border-color: rgba(168, 85, 247, 0.5);
  box-shadow: 0 0 0 3px rgba(168, 85, 247, 0.12);
}
.editor-textarea.small {
  min-height: 60px;
  padding: 10px 14px;
  border-radius: 12px;
}
.editor-textarea::placeholder {
  color: rgba(228, 235, 255, 0.3);
}

.editor-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
}

.submit-btn {
  padding: 10px 24px;
  border-radius: 999px;
  border: none;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #a855f7, #ec4899);
  transition: transform 0.15s, box-shadow 0.15s, opacity 0.15s;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}
.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(168, 85, 247, 0.35);
}
.submit-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.submit-btn.small {
  padding: 6px 16px;
  font-size: 13px;
  margin-top: 8px;
}

.btn-spinner {
  width: 14px; height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

.error-msg {
  margin: 0;
  color: #fb7185;
  font-size: 13px;
}

/* -- Empty State -- */
.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: rgba(228, 235, 255, 0.5);
}
.empty-icon { font-size: 36px; display: block; margin-bottom: 8px; }
.empty-state p { margin: 0; font-size: 14px; }

/* -- Comment Card -- */
.comment-card {
  display: flex;
  gap: 14px;
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.035);
  border: 1px solid rgba(255, 255, 255, 0.05);
  margin-bottom: 14px;
  transition: border-color 0.2s;
}
.comment-card:hover { border-color: rgba(168, 85, 247, 0.2); }

.comment-floor {
  font-size: 12px;
  font-weight: 700;
  color: rgba(168, 85, 247, 0.6);
  padding-top: 2px;
  flex-shrink: 0;
  min-width: 28px;
}

.comment-body { flex: 1; min-width: 0; }

.comment-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.commenter-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #93c5fd;
  font-size: 13px;
  font-weight: 600;
  transition: color 0.15s;
}
.commenter-link:hover { color: #60a5fa; }

.commenter-chip {
  width: 22px; height: 22px;
  border-radius: 50%;
  background: linear-gradient(135deg, #38bdf8, #a855f7);
  display: block;
  object-fit: cover;
  flex-shrink: 0;
}
.reply-chip {
  background: linear-gradient(135deg, #ec4899, #a855f7);
  width: 18px; height: 18px;
}

.reply-arrow {
  color: rgba(228, 235, 255, 0.35);
  font-size: 12px;
}

.comment-time {
  font-size: 12px;
  color: rgba(228, 235, 255, 0.35);
  margin-left: auto;
}

.comment-text {
  font-size: 14px;
  line-height: 1.7;
  color: rgba(228, 235, 255, 0.85);
  word-break: break-word;
}
.reply-text { font-size: 13px; }

.reply-quote,
.reply-target-preview {
  margin: 8px 0;
  padding: 8px 10px;
  border-left: 2px solid rgba(147, 197, 253, 0.45);
  border-radius: 8px;
  background: rgba(147, 197, 253, 0.08);
  color: rgba(228, 235, 255, 0.62);
}

.reply-quote span,
.reply-target-preview span {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  color: rgba(147, 197, 253, 0.82);
}

.reply-quote p,
.reply-target-preview p {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
}

.comment-actions {
  margin-top: 8px;
}

.action-btn {
  border: none;
  background: transparent;
  color: rgba(228, 235, 255, 0.45);
  cursor: pointer;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 8px;
  transition: background 0.15s, color 0.15s;
}
.action-btn:hover {
  background: rgba(168, 85, 247, 0.15);
  color: #c4b5fd;
}

/* -- Reply Thread -- */
.reply-thread {
  margin-top: 14px;
  padding-left: 16px;
  border-left: 2px solid rgba(168, 85, 247, 0.2);
}

.reply-card {
  padding: 12px 14px;
  margin-bottom: 8px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.025);
  border: 1px solid rgba(255, 255, 255, 0.04);
}
.reply-card:last-child { margin-bottom: 0; }

.reply-editor {
  margin-top: 10px;
}

/* -- Comment List -- */
.comment-list {
  margin-top: 0;
}
</style>
