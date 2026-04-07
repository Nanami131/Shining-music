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

      <!-- Stats Cards -->
      <section v-if="userProfile" class="stats-section">
        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-value">{{ userProfile.totalPlayCount || 0 }}</div>
            <div class="stat-label">总播放</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ formatDuration(userProfile.totalPlayDuration || 0) }}</div>
            <div class="stat-label">累计时长</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ userProfile.avgCompletionRate || 0 }}%</div>
            <div class="stat-label">完播率</div>
          </div>
          <div class="stat-card" v-if="userProfile.activeHour != null">
            <div class="stat-value">{{ userProfile.activeHour }}:00</div>
            <div class="stat-label">活跃时段</div>
          </div>
          <div class="stat-card" v-if="topSingerName">
            <div class="stat-value stat-value-text">{{ topSingerName }}</div>
            <div class="stat-label">最爱歌手</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ posts.length }}</div>
            <div class="stat-label">帖子</div>
          </div>
        </div>
      </section>

      <!-- Tabs -->
      <section class="content-section">
        <div class="tab-bar">
          <span
            v-for="tab in tabs"
            :key="tab.key"
            class="tab"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >{{ tab.label }}</span>
        </div>

        <!-- Tab: Top Songs -->
        <div v-if="activeTab === 'topSongs'">
          <div class="dimension-tabs">
            <button
              v-for="d in dimensions"
              :key="d.value"
              class="dim-btn"
              :class="{ active: d.value === selectedDimension }"
              @click="changeDimension(d.value)"
            >{{ d.label }}</button>
          </div>
          <div v-if="topSongsLoading" class="empty">加载中...</div>
          <div v-else-if="topSongs.length === 0" class="empty">暂无播放数据</div>
          <ul v-else class="song-list">
            <li v-for="(item, idx) in topSongs" :key="item.songId" class="song-item" @click="goSong(item.songId)">
              <div class="song-rank" :class="{ gold: idx === 0, silver: idx === 1, bronze: idx === 2 }">{{ idx + 1 }}</div>
              <img :src="item.song.coverUrl || defaultCover" class="song-cover" alt="" />
              <div class="song-info">
                <p class="song-title">{{ item.song.title || `歌曲 ${item.songId}` }}</p>
                <p class="song-meta">{{ item.song.artistName || '未知歌手' }} · 播放 {{ item.playCount }} 次</p>
              </div>
              <button class="play-btn" @click.stop="playSong(item.songId)">▶</button>
            </li>
          </ul>
        </div>

        <!-- Tab: Top Singers -->
        <div v-if="activeTab === 'topSingers'">
          <div v-if="topSingersLoading" class="empty">加载中...</div>
          <div v-else-if="topSingers.length === 0" class="empty">暂无数据</div>
          <div v-else class="singer-grid">
            <div
              v-for="(s, idx) in topSingers"
              :key="s.singerId"
              class="singer-card"
              @click="goSinger(s.singerId)"
            >
              <div class="singer-rank">{{ idx + 1 }}</div>
              <img :src="s.avatarUrl || defaultAvatar" class="singer-avatar" alt="" />
              <p class="singer-name">{{ s.name || `歌手 ${s.singerId}` }}</p>
              <p class="singer-plays">播放 {{ s.playCount }} 次</p>
            </div>
          </div>
        </div>

        <!-- Tab: Posts & Comments -->
        <div v-if="activeTab === 'posts'">
          <h4 class="sub-title" v-if="posts.length">最近帖子</h4>
          <div v-if="posts.length === 0 && recentComments.length === 0" class="empty">
            {{ isSelf ? '你还没有发布任何帖子或评论。' : '该用户暂无动态。' }}
          </div>
          <div v-if="posts.length" class="activity-list">
            <article
              v-for="post in posts.slice(0, 10)"
              :key="'p' + post.id"
              class="activity-card"
              @click="goPostDetail(post.id)"
            >
              <div class="activity-badge">帖子</div>
              <div class="activity-body">
                <h3 class="activity-title">{{ post.title }}</h3>
                <p class="activity-content">{{ makeExcerpt(post.content, 200) }}</p>
                <div class="activity-foot">
                  <span>{{ formatDate(post.createdAt) }}</span>
                  <span>{{ post.commentCount ?? 0 }} 评论</span>
                </div>
              </div>
            </article>
          </div>

          <h4 class="sub-title" v-if="recentComments.length">最近评论</h4>
          <div v-if="recentComments.length" class="activity-list">
            <article
              v-for="c in recentComments"
              :key="'c' + c.id"
              class="activity-card"
              @click="goPostDetail(c.postId)"
            >
              <div class="activity-badge comment-badge">{{ c.parentId ? '回复' : '评论' }}</div>
              <div class="activity-body">
                <p class="activity-context" v-if="postTitleMap[c.postId]">{{ postTitleMap[c.postId] }}</p>
                <p class="activity-content">{{ c.content }}</p>
                <div class="activity-foot">
                  <span>{{ formatDate(c.createdAt) }}</span>
                </div>
              </div>
            </article>
          </div>
        </div>

        <!-- Tab: Playlists -->
        <div v-if="activeTab === 'playlists'">
          <div v-if="playlistsLoading" class="empty">加载中...</div>
          <div v-else-if="playlists.length === 0" class="empty">暂无公开歌单</div>
          <div v-else class="playlist-grid">
            <div
              v-for="pl in playlists"
              :key="pl.id"
              class="playlist-card"
              @click="goPlaylist(pl.id)"
            >
              <img :src="pl.coverUrl || defaultCover" class="playlist-cover" alt="" />
              <p class="playlist-name">{{ pl.name || '未命名歌单' }}</p>
              <p class="playlist-desc">{{ pl.description || '' }}</p>
            </div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script>
import userApi from '@/api/user';
import communityApi from '@/api/community';
import statisticsApi from '@/api/statistics';
import musicApi from '@/api/music';
import defaultAvatar from '@/assets/default-avatar.png';
import defaultCover from '@/assets/default-cover.png';

export default {
  name: 'UserHome',
  data() {
    return {
      targetUserId: null,
      currentUserId: null,
      userInfo: null,
      loading: false,
      defaultAvatar,
      defaultCover,

      userProfile: null,
      topSingerName: null,

      activeTab: 'topSongs',
      tabs: [
        { key: 'topSongs', label: '最爱歌曲' },
        { key: 'topSingers', label: '最爱歌手' },
        { key: 'posts', label: '帖子' },
        { key: 'playlists', label: '歌单' },
      ],

      dimensions: [
        { label: '近7天', value: 'WEEK' },
        { label: '近30天', value: 'MONTH' },
        { label: '全部', value: 'TOTAL' },
      ],
      selectedDimension: 'TOTAL',
      topSongs: [],
      topSongsLoading: false,
      artistNameMap: {},

      topSingers: [],
      topSingersLoading: false,

      posts: [],
      recentComments: [],
      postTitleMap: {},

      playlists: [],
      playlistsLoading: false,
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
    this.loadAll();
  },
  watch: {
    '$route.params.id'(newId) {
      this.targetUserId = Number(newId);
      this.loadAll();
    },
  },
  methods: {
    async loadAll() {
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

      if (this.userInfo) {
        this.loadUserProfile();
        this.loadTopSongs();
        this.loadTopSingers();
        this.loadPlaylists();
        this.loadRecentComments();
      }
    },

    async loadUserProfile() {
      try {
        const res = await statisticsApi.getUserProfile(this.targetUserId);
        if (res.data?.passed && res.data.data) {
          this.userProfile = res.data.data;
          if (this.userProfile.topSingerId) {
            this.loadTopSingerName(this.userProfile.topSingerId);
          }
        }
      } catch { /* silent */ }
    },

    async loadTopSingerName(singerId) {
      try {
        const res = await musicApi.getSingerBaseInfo(singerId);
        if (res.data?.passed && res.data.data) {
          this.topSingerName = res.data.data.name || `歌手 ${singerId}`;
        }
      } catch {
        this.topSingerName = `歌手 ${singerId}`;
      }
    },

    async loadTopSongs() {
      this.topSongsLoading = true;
      try {
        const res = await statisticsApi.getUserTopSongs(this.targetUserId, {
          dimension: this.selectedDimension,
          limit: 10,
        });
        if (res.data?.passed) {
          const list = res.data.data || [];
          const enriched = await Promise.all(
            list.map(async item => {
              const songInfo = await this.fetchSongInfo(item.songId);
              return { songId: item.songId, playCount: item.playCount, song: songInfo };
            })
          );
          this.topSongs = enriched;
        }
      } catch { this.topSongs = []; }
      finally { this.topSongsLoading = false; }
    },

    changeDimension(val) {
      if (this.selectedDimension === val) return;
      this.selectedDimension = val;
      this.loadTopSongs();
    },

    async loadTopSingers() {
      this.topSingersLoading = true;
      try {
        const res = await statisticsApi.getUserTopSingers(this.targetUserId, 10);
        if (res.data?.passed) {
          const list = res.data.data || [];
          const enriched = await Promise.all(
            list.map(async item => {
              const info = await this.fetchSingerInfo(item.singerId);
              return { ...item, ...info };
            })
          );
          this.topSingers = enriched;
        }
      } catch { this.topSingers = []; }
      finally { this.topSingersLoading = false; }
    },

    async loadRecentComments() {
      try {
        const res = await communityApi.getRecentComments(this.targetUserId, 10);
        if (res.data?.passed) {
          this.recentComments = res.data.data || [];
          const postIds = [...new Set(this.recentComments.map(c => c.postId).filter(Boolean))];
          await Promise.all(postIds.map(async pid => {
            if (this.postTitleMap[pid]) return;
            try {
              const pr = await communityApi.getPostDetails(pid);
              if (pr.data?.passed && pr.data.data) {
                this.postTitleMap[pid] = pr.data.data.title || `帖子 #${pid}`;
              }
            } catch { /* silent */ }
          }));
        }
      } catch { this.recentComments = []; }
    },

    async loadPlaylists() {
      this.playlistsLoading = true;
      try {
        const res = await musicApi.discoverPlaylists(this.targetUserId);
        if (res.data?.passed) {
          const all = res.data.data || [];
          this.playlists = all.filter(p => p.userId === this.targetUserId);
        }
      } catch { this.playlists = []; }
      finally { this.playlistsLoading = false; }
    },

    async fetchSongInfo(songId) {
      try {
        const res = await musicApi.getSongBaseInfo(songId);
        if (res.data?.passed && res.data.data) {
          const song = res.data.data;
          if (song.artistId) {
            song.artistName = await this.fetchArtistName(song.artistId);
          }
          return song;
        }
      } catch { /* silent */ }
      return {};
    },

    async fetchArtistName(artistId) {
      if (!artistId) return '';
      if (this.artistNameMap[artistId]) return this.artistNameMap[artistId];
      try {
        const res = await musicApi.getSingerBaseInfo(artistId);
        const name = res.data?.passed && res.data.data?.name ? res.data.data.name : `歌手 ${artistId}`;
        this.artistNameMap[artistId] = name;
        return name;
      } catch {
        const fb = `歌手 ${artistId}`;
        this.artistNameMap[artistId] = fb;
        return fb;
      }
    },

    async fetchSingerInfo(singerId) {
      try {
        const res = await musicApi.getSingerBaseInfo(singerId);
        if (res.data?.passed && res.data.data) return res.data.data;
      } catch { /* silent */ }
      return { name: `歌手 ${singerId}`, avatarUrl: null };
    },

    formatDuration(seconds) {
      if (!seconds || seconds <= 0) return '0分';
      const h = Math.floor(seconds / 3600);
      const m = Math.floor((seconds % 3600) / 60);
      if (h > 0) return `${h}h${m}m`;
      return `${m}分钟`;
    },

    makeExcerpt(content, maxLen = 100) {
      if (!content) return '';
      const plain = String(content).replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
      return plain.length > maxLen ? plain.slice(0, maxLen) + '…' : plain;
    },

    formatDate(val) {
      if (!val) return '';
      try { return new Date(val).toLocaleDateString(); } catch { return String(val); }
    },

    playSong(songId) {
      this.$bus.emit('playSong', { songId });
    },

    goSong(id) { this.$router.push(`/song/${id}`); },
    goSinger(id) { this.$router.push(`/singer/${id}`); },
    goPostDetail(id) { this.$router.push({ name: 'post-detail', params: { id } }); },
    goPlaylist(id) { this.$router.push(`/playlist/${id}`); },
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

.user-meta { flex: 1; min-width: 200px; }
.nick { font-size: clamp(24px, 4vw, 38px); margin: 0 0 6px; font-weight: 700; }
.sig { color: rgba(228, 235, 255, 0.7); font-size: 15px; margin: 0 0 10px; }
.tags { display: flex; gap: 8px; flex-wrap: wrap; }
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
.edit-btn:hover { background: rgba(255, 255, 255, 0.1); transform: translateY(-2px); }

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

/* ---- Stats Cards ---- */
.stats-section {
  max-width: 900px;
  margin: 0 auto 8px;
  padding: 0 clamp(20px, 5vw, 60px);
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 12px;
}
.stat-card {
  padding: 18px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
  text-align: center;
  transition: transform 0.2s, border-color 0.2s;
}
.stat-card:hover { transform: translateY(-3px); border-color: rgba(56, 189, 248, 0.3); }
.stat-value { font-size: 22px; font-weight: 700; margin-bottom: 4px; }
.stat-value-text { font-size: 16px; }
.stat-label { font-size: 12px; color: rgba(228, 235, 255, 0.55); }

/* ---- Content ---- */
.content-section {
  max-width: 900px;
  margin: 0 auto;
  padding: 16px clamp(20px, 5vw, 60px) 0;
}

.tab-bar {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  margin-bottom: 24px;
}
.tab {
  padding: 10px 4px;
  font-size: 15px;
  color: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  position: relative;
  transition: color 0.2s;
}
.tab:hover { color: rgba(255, 255, 255, 0.8); }
.tab.active { color: #f2f5ff; }
.tab.active::after {
  content: '';
  position: absolute;
  left: 0; right: 0; bottom: -1px;
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

/* ---- Dimension Tabs ---- */
.dimension-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.dim-btn {
  padding: 5px 14px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: transparent;
  color: rgba(228, 235, 255, 0.6);
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}
.dim-btn.active {
  background: linear-gradient(120deg, rgba(56, 189, 248, 0.3), rgba(168, 85, 247, 0.3));
  color: #f2f5ff;
  border-color: rgba(56, 189, 248, 0.4);
}

/* ---- Song List ---- */
.song-list {
  list-style: none;
  margin: 0; padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.song-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.05);
  cursor: pointer;
  transition: transform 0.15s, border-color 0.2s;
}
.song-item:hover { transform: translateX(4px); border-color: rgba(56, 189, 248, 0.3); }
.song-rank {
  width: 28px; height: 28px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 13px; flex-shrink: 0;
}
.song-rank.gold { background: linear-gradient(135deg, #fbbf24, #f59e0b); color: #1c1917; }
.song-rank.silver { background: linear-gradient(135deg, #94a3b8, #cbd5e1); color: #1e293b; }
.song-rank.bronze { background: linear-gradient(135deg, #d97706, #b45309); color: #fff; }
.song-cover {
  width: 44px; height: 44px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}
.song-info { flex: 1; min-width: 0; }
.song-title { margin: 0; font-size: 15px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.song-meta { margin: 2px 0 0; font-size: 13px; color: rgba(228, 235, 255, 0.5); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.play-btn {
  width: 32px; height: 32px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #a855f7, #ec4899);
  color: #fff;
  cursor: pointer;
  font-size: 12px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  transition: transform 0.15s, box-shadow 0.15s;
}
.play-btn:hover { transform: scale(1.1); box-shadow: 0 4px 12px rgba(168, 85, 247, 0.4); }

/* ---- Singer Grid ---- */
.singer-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 14px;
}
.singer-card {
  padding: 20px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  text-align: center;
  cursor: pointer;
  position: relative;
  transition: transform 0.2s, border-color 0.2s;
}
.singer-card:hover { transform: translateY(-4px); border-color: rgba(168, 85, 247, 0.4); }
.singer-rank {
  position: absolute;
  top: 10px; left: 10px;
  width: 24px; height: 24px;
  border-radius: 50%;
  background: rgba(168, 85, 247, 0.5);
  font-size: 12px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.singer-avatar {
  width: 72px; height: 72px;
  border-radius: 50%;
  object-fit: cover;
  margin: 0 auto 10px;
  display: block;
  border: 2px solid rgba(255, 255, 255, 0.1);
}
.singer-name { margin: 0; font-size: 14px; font-weight: 600; }
.singer-plays { margin: 4px 0 0; font-size: 12px; color: rgba(228, 235, 255, 0.5); }

/* ---- Sub Title ---- */
.sub-title {
  font-size: 14px;
  color: rgba(228, 235, 255, 0.6);
  margin: 0 0 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 1px;
}

/* ---- Activity List ---- */
.activity-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}
.activity-card {
  display: flex;
  gap: 14px;
  padding: 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  cursor: pointer;
  transition: transform 0.15s, border-color 0.2s;
}
.activity-card:hover { transform: translateX(4px); border-color: rgba(56, 189, 248, 0.3); }
.activity-badge {
  padding: 4px 10px;
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(56, 189, 248, 0.25), rgba(168, 85, 247, 0.25));
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
  align-self: flex-start;
  color: #93c5fd;
}
.comment-badge {
  background: linear-gradient(135deg, rgba(236, 72, 153, 0.25), rgba(168, 85, 247, 0.25));
  color: #f9a8d4;
}
.activity-body { flex: 1; min-width: 0; }
.activity-title { margin: 0; font-size: 15px; font-weight: 600; }
.activity-context {
  margin: 0 0 4px;
  font-size: 12px;
  color: rgba(228, 235, 255, 0.5);
}
.activity-content {
  margin: 6px 0 0;
  font-size: 14px;
  color: rgba(228, 235, 255, 0.75);
  line-height: 1.6;
}
.activity-foot {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  font-size: 12px;
  color: rgba(228, 235, 255, 0.45);
}

/* ---- Post Grid ---- */
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
.post-card:hover { transform: translateY(-4px); border-color: rgba(56, 189, 248, 0.4); }
.post-card h3 { font-size: 16px; margin: 0; }
.excerpt { color: rgba(228, 235, 255, 0.7); font-size: 14px; flex: 1; }
.post-foot {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: rgba(228, 235, 255, 0.5);
}

/* ---- Playlist Grid ---- */
.playlist-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}
.playlist-card {
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, border-color 0.2s;
}
.playlist-card:hover { transform: translateY(-4px); border-color: rgba(56, 189, 248, 0.3); }
.playlist-cover {
  width: 100%;
  height: 140px;
  object-fit: cover;
  display: block;
}
.playlist-name {
  margin: 10px 14px 2px;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.playlist-desc {
  margin: 0 14px 12px;
  font-size: 12px;
  color: rgba(228, 235, 255, 0.5);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
