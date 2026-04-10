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
          <button class="glass ranking-cta" @click="goTo('/ranking')">热门排行榜</button>
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

    <section v-if="dailyRecommendations.length" class="recommend-panel">
      <div class="panel-head">
        <div>
          <h2>每日推荐 · 基于你的听歌画像</h2>
          <p>由 Ribecky (2021) 多维相似度算法驱动，语种分流 + 五维加权匹配。</p>
        </div>
        <div class="panel-head-actions">
          <button class="ghost" @click="playAllRecommendations" v-if="dailyRecommendations.length">一键播放全部</button>
          <button class="ghost" @click="refreshRecommendations">刷新推荐</button>
        </div>
      </div>
      <div class="recommend-grid">
        <article
          v-for="(rec, idx) in dailyRecommendations"
          :key="rec.songId"
          class="recommend-card"
          @click="goTo(`/song/${rec.songId}`)"
        >
          <div class="rec-rank">{{ idx + 1 }}</div>
          <div class="rec-cover" :style="{ backgroundImage: rec.coverUrl ? `url(${rec.coverUrl})` : '' }">
            <span v-if="!rec.coverUrl" class="rec-icon">♪</span>
          </div>
          <div class="rec-info">
            <h4>{{ rec.title || `歌曲 #${rec.songId}` }}</h4>
            <p class="rec-artist">{{ rec.singerName || '未知歌手' }}</p>
            <div class="rec-match">
              <div class="match-bar">
                <div class="match-fill" :style="{ width: (rec.similarity * 100) + '%' }"></div>
              </div>
              <span class="match-pct">{{ (rec.similarity * 100).toFixed(1) }}%</span>
            </div>
          </div>
          <button
            class="rec-play-btn"
            title="播放"
            @click.stop="playRecSong(rec, idx)"
          >▶</button>
          <button
            class="rec-add-btn"
            :class="{ added: rec._added }"
            :title="rec._added ? '已加入歌单' : '加入当前歌单'"
            @click.stop="addRecToCurrentPlaylist(rec)"
          >{{ rec._added ? '✓' : '+' }}</button>
        </article>
      </div>
    </section>

    <section v-else-if="isLoggedIn" class="recommend-panel recommend-empty">
      <div class="panel-head">
        <div>
          <h2>每日推荐 · 基于你的听歌画像</h2>
          <p>多听几首歌，推荐引擎就能认识你的口味了。</p>
        </div>
      </div>
    </section>

    <section v-if="cfRecommendations.length" class="recommend-panel cf-panel">
      <div class="panel-head">
        <div>
          <h2>协同发现 · 和你口味相近的人也在听</h2>
          <p>Item-based Collaborative Filtering — 基于用户群体的共同偏好挖掘相似歌曲。</p>
        </div>
        <div class="panel-head-actions">
          <button class="ghost" @click="playAllCF" v-if="cfRecommendations.length">一键播放全部</button>
        </div>
      </div>
      <div class="recommend-grid">
        <article
          v-for="(rec, idx) in cfRecommendations"
          :key="'cf-' + rec.songId"
          class="recommend-card"
          @click="goTo(`/song/${rec.songId}`)"
        >
          <div class="rec-rank cf-rank">{{ idx + 1 }}</div>
          <div class="rec-cover" :style="{ backgroundImage: rec.coverUrl ? `url(${rec.coverUrl})` : '' }">
            <span v-if="!rec.coverUrl" class="rec-icon">♪</span>
          </div>
          <div class="rec-info">
            <h4>{{ rec.title || `歌曲 #${rec.songId}` }}</h4>
            <p class="rec-artist">{{ rec.singerName || '未知歌手' }}</p>
            <div class="rec-match">
              <div class="match-bar cf-bar">
                <div class="match-fill cf-fill" :style="{ width: (rec.similarity * 100) + '%' }"></div>
              </div>
              <span class="match-pct cf-pct">{{ (rec.similarity * 100).toFixed(1) }}%</span>
            </div>
          </div>
          <button
            class="rec-play-btn"
            title="播放"
            @click.stop="playCFSong(rec, idx)"
          >▶</button>
        </article>
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

    <section class="video-panel">
      <div class="panel-head">
        <div>
          <h2>VIDEOS · 视频展映</h2>
          <p>聚合演出片段、MV 与现场录像，快速浏览站内视频内容。</p>
        </div>
        <button class="ghost" @click="goTo('/songs')">进入内容页</button>
      </div>
      <div class="video-grid">
        <article class="video-card" v-for="video in featuredVideos" :key="video.id" @click="goToVideo(video.id)">
          <div class="video-cover" :style="{ '--video-accent': video.accent }">
            <span class="video-duration">{{ video.duration }}</span>
            <span class="video-play">▶</span>
          </div>
          <div class="video-meta">
            <h3>{{ video.title }}</h3>
            <p>{{ video.singer }}</p>
            <p class="video-sub">{{ video.desc }}</p>
          </div>
        </article>
        <article v-if="!featuredVideos.length" class="video-card video-empty">
          <div class="video-meta">
            <h3>暂无视频</h3>
            <p>请先前往开发者页面上传视频。</p>
            <p class="video-sub">路径：/dev/video</p>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script>
import musicApi from '@/api/music';
import statisticsApi from '@/api/statistics';
import recommendApi from '@/api/recommend';

const MIX_TEMPLATES = [
  {
    title: (s1, s2) => `${s1} · 霓虹电气篇`,
    desc: (s1, s2) => `从《${s1}》到《${s2}》，把城市霓虹揉进节拍里。`,
    badges: ['Synthwave', '都市夜色', '重低鼓'],
    tag: 'LIVE NOW',
    accent: '#f472b6',
  },
  {
    title: (s1) => `${s1} · 透明呼吸`,
    desc: (s1, s2) => `《${s1}》的空灵人声衔接《${s2}》，营造失重的水下质感。`,
    badges: ['Dream Pop', '沉浸', '空灵人声'],
    tag: '编辑推荐',
    accent: '#38bdf8',
  },
  {
    title: (s1) => `${s1} · 童话旋律`,
    desc: (s1, s2) => `以《${s1}》和《${s2}》衍生的原声合集，适合写字和发呆。`,
    badges: ['原声', '治愈', '木吉他'],
    tag: '温柔上线',
    accent: '#facc15',
  },
];

const MOOD_TEMPLATES = [
  { title: '凌晨写稿', desc: '屏幕泛蓝的凌晨，键盘声和鼓点同步。', tag: 'Night Shift', emoji: '🌃', tips: ['低饱和电子律动', '轻人声样本', '120 BPM 左右'] },
  { title: '午后梦游', desc: '阳光柔焦到木质桌面，灵感慢慢酝酿。', tag: 'Lazy Noon', emoji: '🌤️', tips: ['Lo-fi hiphop', '口风琴点缀', '轻打击'] },
  { title: '黄昏疾驰', desc: '地铁窗外闪过的灯带，与耳机里的合成器共鸣。', tag: 'City Rush', emoji: '🚇', tips: ['Future Bass', '切分节奏', '厚重贝斯'] },
];

function shuffle(arr) {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

export default {
  name: 'ShiningHome',
  data() {
    return {
      allSongs: [],
      dailyRecommendations: [],
      cfRecommendations: [],
      isLoggedIn: false,
      heroStats: [
        { value: '--', label: '累计播放' },
        { value: '--', label: '曲库收录' },
        { value: '--', label: '平均完播率' },
      ],
      featuredMixes: [],
      moodSets: [],
      featuredVideos: [],
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
  async created() {
    let userBase = {};
    try { userBase = JSON.parse(localStorage.getItem('userBase') || '{}'); } catch (e) { /* ignore */ }
    this.isLoggedIn = !!userBase.id;
    await this.loadDynamicContent();
    this.loadFeaturedVideos();
    if (this.isLoggedIn) {
      this.loadRecommendations(userBase.id);
      this.loadCFRecommendations(userBase.id);
    }
  },
  computed: {
    songTitlesPreview() {
      if (!this.allSongs.length) return '...';
      return this.allSongs.slice(0, 4).map((s) => s.title).join('、');
    },
  },
  methods: {
    async loadDynamicContent() {
      try {
        const [songsRes, topRes] = await Promise.all([
          musicApi.getSongs().catch(() => null),
          statisticsApi.getGlobalTopSongs(10).catch(() => null),
        ]);

        const songs = songsRes?.data?.passed ? (songsRes.data.data || []) : [];
        this.allSongs = songs;
        const songCount = songs.length;

        let totalPlayCount = 0;
        const topSongs = topRes?.data?.passed ? (topRes.data.data || []) : [];
        topSongs.forEach(t => { totalPlayCount += (t.playCount || 0); });

        let userBase = {};
        try { userBase = JSON.parse(localStorage.getItem('userBase') || '{}'); } catch (e) { /* ignore */ }
        const userId = userBase.id;
        let profile = null;
        if (userId) {
          try {
            const profileRes = await statisticsApi.getUserProfile(userId);
            if (profileRes?.data?.passed) profile = profileRes.data.data;
          } catch (e) { /* silent */ }
        }

        this.heroStats = [
          { value: totalPlayCount > 0 ? `${totalPlayCount}` : (profile ? `${profile.totalPlayCount}` : '--'), label: '全站播放' },
          { value: songCount ? `${songCount}` : '--', label: '曲库收录' },
          { value: profile ? `${profile.avgCompletionRate}%` : '--', label: '我的完播率' },
        ];

        this.buildFeaturedMixes(songs);
        this.buildMoodSets(songs);
      } catch (e) { /* keep defaults */ }
    },
    buildFeaturedMixes(songs) {
      if (songs.length < 6) return;
      const pool = shuffle(songs);
      this.featuredMixes = MIX_TEMPLATES.map((tpl, i) => {
        const s1 = pool[i * 2];
        const s2 = pool[i * 2 + 1];
        return {
          title: tpl.title(s1.title, s2.title),
          desc: tpl.desc(s1.title, s2.title),
          badges: tpl.badges,
          tag: tpl.tag,
          accent: tpl.accent,
          route: `/song/${s1.id}`,
          highlightSong: { id: s1.id, title: s1.title },
        };
      });
    },
    buildMoodSets(songs) {
      if (songs.length < 9) return;
      const pool = shuffle(songs);
      this.moodSets = MOOD_TEMPLATES.map((tpl, i) => ({
        ...tpl,
        songs: pool.slice(i * 3, i * 3 + 3).map(s => ({ id: s.id, title: s.title })),
      }));
    },
    async loadFeaturedVideos() {
      try {
        const response = await musicApi.listVideos();
        if (response.data?.passed) {
          const colorPool = ['#f472b6', '#38bdf8', '#facc15', '#34d399', '#a78bfa', '#fb7185'];
          const list = response.data.data || [];
          this.featuredVideos = list.slice(0, 8).map((item, index) => ({
            id: item.id,
            title: item.title || `视频${item.id}`,
            singer: item.singerId ? `歌手 ${item.singerId}` : '未绑定歌手',
            duration: '--:--',
            desc: item.fileUrl ? '已上传视频，可点击进入播放页。' : '视频资源待就绪。',
            accent: colorPool[index % colorPool.length],
          }));
        } else {
          this.featuredVideos = [];
        }
      } catch (error) {
        this.featuredVideos = [];
      }
    },
    async loadRecommendations(userId) {
      try {
        const res = await recommendApi.getDailyRecommendations(userId, 10);
        if (res?.data?.passed && Array.isArray(res.data.data)) {
          const recs = res.data.data;
          const songMap = {};
          this.allSongs.forEach(s => { songMap[s.id] = s; });

          const enriched = [];
          for (const rec of recs) {
            const song = songMap[rec.songId];
            if (song) {
              enriched.push({
                songId: rec.songId,
                similarity: rec.similarity,
                title: song.title,
                singerName: song.singerName || song.singer || '',
                coverUrl: song.coverUrl || song.pic || '',
              });
            } else {
              try {
                const infoRes = await musicApi.getSongBaseInfo(rec.songId);
                const info = infoRes?.data?.passed ? infoRes.data.data : null;
                enriched.push({
                  songId: rec.songId,
                  similarity: rec.similarity,
                  title: info?.title || `歌曲 #${rec.songId}`,
                  singerName: info?.singerName || '',
                  coverUrl: info?.coverUrl || info?.pic || '',
                });
              } catch (e) {
                enriched.push({ songId: rec.songId, similarity: rec.similarity, title: `歌曲 #${rec.songId}`, singerName: '', coverUrl: '' });
              }
            }
          }
          this.dailyRecommendations = enriched;
        }
      } catch (e) { /* silent */ }
    },
    async loadCFRecommendations(userId) {
      try {
        const res = await recommendApi.getItemCFRecommendations(userId, 10);
        if (res?.data?.passed && Array.isArray(res.data.data)) {
          const recs = res.data.data;
          const songMap = {};
          this.allSongs.forEach(s => { songMap[s.id] = s; });

          const enriched = [];
          for (const rec of recs) {
            const song = songMap[rec.songId];
            if (song) {
              enriched.push({
                songId: rec.songId,
                similarity: rec.similarity,
                title: song.title,
                singerName: song.singerName || song.singer || '',
                coverUrl: song.coverUrl || song.pic || '',
              });
            } else {
              try {
                const infoRes = await musicApi.getSongBaseInfo(rec.songId);
                const info = infoRes?.data?.passed ? infoRes.data.data : null;
                enriched.push({
                  songId: rec.songId,
                  similarity: rec.similarity,
                  title: info?.title || `歌曲 #${rec.songId}`,
                  singerName: info?.singerName || '',
                  coverUrl: info?.coverUrl || info?.pic || '',
                });
              } catch (e) {
                enriched.push({ songId: rec.songId, similarity: rec.similarity, title: `歌曲 #${rec.songId}`, singerName: '', coverUrl: '' });
              }
            }
          }
          this.cfRecommendations = enriched;
        }
      } catch (e) { /* silent */ }
    },
    playCFSong(rec, idx) {
      const songIds = this.cfRecommendations.map(r => r.songId);
      this.$bus.emit('playSong', {
        songId: rec.songId,
        playlist: songIds,
        index: idx,
        source: 'recommend-cf',
      });
    },
    playAllCF() {
      if (!this.cfRecommendations.length) return;
      const songIds = this.cfRecommendations.map(r => r.songId);
      this.$bus.emit('playSong', {
        songId: songIds[0],
        playlist: songIds,
        index: 0,
        source: 'recommend-cf',
      });
    },
    async refreshRecommendations() {
      let userBase = {};
      try { userBase = JSON.parse(localStorage.getItem('userBase') || '{}'); } catch (e) { /* ignore */ }
      if (userBase.id) {
        this.dailyRecommendations = [];
        await this.loadRecommendations(userBase.id);
      }
    },
    playRecSong(rec, idx) {
      const songIds = this.dailyRecommendations.map(r => r.songId);
      this.$bus.emit('playSong', {
        songId: rec.songId,
        playlist: songIds,
        index: idx,
        source: 'recommend',
      });
    },
    playAllRecommendations() {
      if (!this.dailyRecommendations.length) return;
      const songIds = this.dailyRecommendations.map(r => r.songId);
      this.$bus.emit('playSong', {
        songId: songIds[0],
        playlist: songIds,
        index: 0,
        source: 'recommend',
      });
    },
    async addRecToCurrentPlaylist(rec) {
      if (rec._added) return;
      let userBase = {};
      try { userBase = JSON.parse(localStorage.getItem('userBase') || '{}'); } catch (e) { /* ignore */ }
      const userId = userBase.id;
      if (!userId) return;
      try {
        const currentRes = await musicApi.getCurrentPlaylist(userId);
        const currentPlaylistId = currentRes.data?.data?.id;
        if (!currentPlaylistId) return;
        const resp = await musicApi.managePlaylistSong({
          playlistId: currentPlaylistId,
          songId: rec.songId,
          action: 'add',
        });
        if (resp.data?.passed) {
          rec._added = true;
          this.$bus.emit('refreshCurrentPlaylist');
        }
      } catch (e) { /* silent */ }
    },
    goTo(path) {
      this.$router.push(path);
    },
    goToVideo(videoId) {
      this.$router.push(`/video/${videoId}`);
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

.ranking-cta {
  background: rgba(245, 158, 11, 0.2);
  border-color: rgba(245, 158, 11, 0.5);
  color: #fbbf24;
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
.mood-panel,
.video-panel {
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

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 18px;
}

.video-card {
  border-radius: 22px;
  background: rgba(1, 3, 20, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.08);
  overflow: hidden;
  cursor: pointer;
}

.video-empty {
  cursor: default;
}

.video-cover {
  height: 140px;
  background:
    radial-gradient(circle at 25% 25%, var(--video-accent), transparent 60%),
    linear-gradient(135deg, rgba(9, 12, 40, 0.9), rgba(24, 24, 72, 0.85));
  position: relative;
}

.video-duration {
  position: absolute;
  right: 10px;
  bottom: 10px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(2, 6, 23, 0.7);
  font-size: 12px;
}

.video-play {
  position: absolute;
  left: 12px;
  bottom: 8px;
  font-size: 20px;
  color: var(--video-accent);
}

.video-meta {
  padding: 12px 14px 14px;
}

.video-meta h3 {
  margin: 0 0 4px;
  font-size: 16px;
}

.video-meta p {
  margin: 0;
  font-size: 13px;
  color: rgba(236, 242, 255, 0.78);
}

.video-sub {
  margin-top: 6px !important;
  color: rgba(236, 242, 255, 0.62) !important;
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

.recommend-panel {
  padding: clamp(20px, 4vw, 28px);
  border-radius: 28px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.12), rgba(168, 85, 247, 0.1));
  border: 1px solid rgba(168, 85, 247, 0.2);
  box-shadow: 0 25px 55px rgba(1, 2, 23, 0.6);
}

.recommend-empty {
  opacity: 0.7;
}

.recommend-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.recommend-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border-radius: 18px;
  background: rgba(1, 3, 20, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.06);
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease;
  position: relative;
}

.panel-head-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.rec-play-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid rgba(56, 189, 248, 0.4);
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.rec-play-btn:hover {
  background: rgba(56, 189, 248, 0.35);
  border-color: #38bdf8;
  transform: scale(1.1);
}

.rec-add-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid rgba(168, 85, 247, 0.4);
  background: rgba(168, 85, 247, 0.15);
  color: #a855f7;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.rec-add-btn:hover {
  background: rgba(168, 85, 247, 0.35);
  border-color: #a855f7;
  transform: scale(1.1);
}

.rec-add-btn.added {
  background: rgba(34, 197, 94, 0.2);
  border-color: rgba(34, 197, 94, 0.5);
  color: #22c55e;
  cursor: default;
}

.recommend-card:hover {
  transform: translateY(-2px);
  border-color: rgba(168, 85, 247, 0.35);
}

.rec-rank {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, #a855f7, #6366f1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

.rec-cover {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.3), rgba(168, 85, 247, 0.2));
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.rec-icon {
  font-size: 22px;
  opacity: 0.5;
}

.rec-info {
  flex: 1;
  min-width: 0;
}

.rec-info h4 {
  margin: 0 0 2px;
  font-size: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rec-artist {
  margin: 0 0 6px;
  font-size: 12px;
  color: rgba(236, 242, 255, 0.6);
}

.rec-match {
  display: flex;
  align-items: center;
  gap: 8px;
}

.match-bar {
  flex: 1;
  height: 4px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.1);
  overflow: hidden;
}

.match-fill {
  height: 100%;
  border-radius: 2px;
  background: linear-gradient(90deg, #a855f7, #38bdf8);
  transition: width 0.6s ease;
}

.match-pct {
  font-size: 11px;
  color: rgba(168, 85, 247, 0.9);
  font-family: 'JetBrains Mono', monospace;
  white-space: nowrap;
}

.cf-panel {
  background: linear-gradient(135deg, rgba(34, 197, 94, 0.1), rgba(56, 189, 248, 0.08));
  border-color: rgba(34, 197, 94, 0.2);
}

.cf-rank {
  background: linear-gradient(135deg, #22c55e, #06b6d4);
}

.cf-fill {
  background: linear-gradient(90deg, #22c55e, #06b6d4);
}

.cf-pct {
  color: rgba(34, 197, 94, 0.9);
}

@media (max-width: 768px) {
  .hero-stats {
    flex-direction: column;
  }
}
</style>
