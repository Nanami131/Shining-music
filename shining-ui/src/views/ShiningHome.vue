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

    <section v-if="isLoggedIn" class="recommend-panel cf-panel">
      <div class="panel-head">
        <div>
          <h2>协同发现 · 和你口味相近的人也在听</h2>
          <p>{{ cfStatusText }}</p>
        </div>
        <div class="panel-head-actions">
          <button class="ghost" @click="playAllCF" v-if="cfRecommendations.length">一键播放全部</button>
          <button class="ghost" @click="refreshCFRecommendations(true)" :disabled="cfLoading || cfRebuilding">重试</button>
          <button class="ghost" @click="rebuildCFMatrix" :disabled="cfLoading || cfRebuilding">
            {{ cfRebuilding ? '重建中...' : '重建矩阵' }}
          </button>
        </div>
      </div>
      <div v-if="cfRecommendations.length" class="recommend-grid">
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
      <div v-else class="recommend-placeholder">
        <p>{{ cfLoading ? '正在加载协同过滤推荐...' : (cfError || cfMessage || '暂无协同过滤推荐。') }}</p>
      </div>
    </section>

    <section class="recommend-panel random-panel">
      <div class="panel-head">
        <div>
          <h2>随机漫游 · 20首随机歌曲</h2>
          <p>从全部曲库中随机抽取，发现意想不到的好歌。</p>
        </div>
        <div class="panel-head-actions">
          <button class="ghost" @click="playAllRandom" v-if="randomSongs.length">一键播放全部</button>
          <button class="ghost" @click="saveRandomAsPlaylist" v-if="randomSongs.length" :disabled="randomSaving">
            {{ randomSaving ? '保存中...' : '保存为歌单' }}
          </button>
          <button class="ghost" @click="refreshRandom" :disabled="randomLoading">
            {{ randomLoading ? '加载中...' : '换一批' }}
          </button>
        </div>
      </div>
      <div v-if="randomSongs.length" class="recommend-grid">
        <article
          v-for="(song, idx) in randomSongs"
          :key="'rand-' + song.id"
          class="recommend-card"
          @click="goTo(`/song/${song.id}`)"
        >
          <div class="rec-rank random-rank">{{ idx + 1 }}</div>
          <div class="rec-cover" :style="{ backgroundImage: song.coverUrl ? `url(${song.coverUrl})` : '' }">
            <span v-if="!song.coverUrl" class="rec-icon">♪</span>
          </div>
          <div class="rec-info">
            <h4>{{ song.title }}</h4>
            <p class="rec-artist">{{ song.artistName || '未知歌手' }}</p>
          </div>
          <button
            class="rec-play-btn"
            title="播放"
            @click.stop="playRandomSong(song, idx)"
          >▶</button>
        </article>
      </div>
      <div v-else class="recommend-placeholder">
        <p>{{ randomLoading ? '正在加载随机推荐...' : '暂无数据' }}</p>
      </div>
    </section>

    <section class="featured-panel">
      <div class="panel-head">
        <div>
          <h2>发现歌单 · 来自社区和官方的精选合集</h2>
          <p>公开歌单与官方精选，点击即可收听。</p>
        </div>
        <button class="ghost" @click="goTo('/playlists')">查看全部歌单</button>
      </div>

      <div class="featured-grid">
        <article
          v-for="pl in discoverList"
          :key="pl.id"
          class="featured-card"
          :style="{ '--accent': pl.accent }"
          @click="goTo(`/playlist/${pl.id}`)"
        >
          <header>
            <p class="tag">{{ pl.isOfficial ? '官方精选' : '社区歌单' }}</p>
            <span class="track-id">#{{ pl.id }}</span>
          </header>
          <div class="playlist-cover-row" v-if="pl.coverUrl">
            <img :src="pl.coverUrl" class="playlist-thumb" alt="" />
          </div>
          <h3>{{ pl.name }}</h3>
          <p>{{ pl.description || '暂无描述' }}</p>
          <div class="track-highlight">
            <p>by {{ pl.nickName || '未知' }}</p>
            <button class="mini-btn" @click.stop="goTo(`/playlist/${pl.id}`)">进入歌单</button>
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
            <img v-if="video.coverUrl" :src="video.coverUrl" class="video-cover-img" alt="" />
            <span class="video-play">▶</span>
          </div>
          <div class="video-meta">
            <h3>{{ video.title }}</h3>
            <p>{{ video.singer }}</p>
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

export default {
  name: 'ShiningHome',
  data() {
    return {
      allSongs: [],
      dailyRecommendations: [],
      cfRecommendations: [],
      cfLoading: false,
      cfRebuilding: false,
      cfMessage: '',
      cfError: '',
      isLoggedIn: false,
      heroStats: [
        { value: '--', label: '累计播放' },
        { value: '--', label: '曲库收录' },
        { value: '--', label: '平均完播率' },
      ],
      randomSongs: [],
      randomLoading: false,
      randomSaving: false,
      discoverList: [],
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
    this.loadRandomSongs();
    if (this.isLoggedIn) {
      this.loadRecommendations(userBase.id);
      this.loadCFRecommendations(userBase.id);
    }
  },
  computed: {
    cfStatusText() {
      if (this.cfRebuilding) return '正在根据播放历史重建 Item-CF 相似度矩阵。';
      if (this.cfLoading) return '正在读取 Item-CF 推荐结果。';
      if (this.cfError) return this.cfError;
      if (this.cfMessage) return this.cfMessage;
      return 'Item-based Collaborative Filtering — 基于用户群体的共同偏好挖掘相似歌曲。';
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

        this.loadDiscoverPlaylists();
      } catch (e) { /* keep defaults */ }
    },
    async loadDiscoverPlaylists() {
      try {
        const colorPool = ['#f472b6', '#38bdf8', '#facc15', '#34d399', '#a78bfa', '#fb7185'];
        const res = await musicApi.discoverPlaylists();
        if (res?.data?.passed) {
          this.discoverList = (res.data.data || []).slice(0, 6).map((pl, i) => ({
            ...pl,
            isOfficial: pl.userId === -1,
            accent: colorPool[i % colorPool.length],
          }));
        }
      } catch (e) { /* silent */ }
    },
    async loadFeaturedVideos() {
      try {
        const response = await musicApi.listVideos();
        if (response.data?.passed) {
          const colorPool = ['#f472b6', '#38bdf8', '#facc15', '#34d399', '#a78bfa', '#fb7185'];
          const list = response.data.data || [];
          const videos = list.slice(0, 8).map((item, index) => ({
            id: item.id,
            title: item.title || `视频${item.id}`,
            singerId: item.singerId,
            singer: item.singerId ? `歌手 ${item.singerId}` : '未绑定歌手',
            coverUrl: item.coverUrl || null,
            accent: colorPool[index % colorPool.length],
          }));
          this.featuredVideos = videos;
          const singerIds = [...new Set(videos.map(v => v.singerId).filter(Boolean))];
          for (const sid of singerIds) {
            try {
              const res = await musicApi.getSingerBaseInfo(sid);
              if (res.data?.passed && res.data.data?.name) {
                const name = res.data.data.name;
                this.featuredVideos.forEach(v => {
                  if (v.singerId === sid) v.singer = name;
                });
              }
            } catch (_) { /* ignore */ }
          }
        } else {
          this.featuredVideos = [];
        }
      } catch (error) {
        this.featuredVideos = [];
      }
    },
    async enrichRecommendations(recs) {
      const songMap = {};
      this.allSongs.forEach(s => { songMap[s.id] = s; });

      const songs = await Promise.all(recs.map(async (rec) => {
        let song = songMap[rec.songId];
        if (!song) {
          try {
            const infoRes = await musicApi.getSongBaseInfo(rec.songId);
            song = infoRes?.data?.passed ? infoRes.data.data : null;
          } catch (e) { /* ignore */ }
        }
        return { rec, song };
      }));

      const singerCache = {};
      const enriched = [];
      for (const { rec, song } of songs) {
        let singerName = song?.singerName || song?.singer || '';
        if (!singerName && song?.artistId) {
          const aid = song.artistId;
          if (singerCache[aid] === undefined) {
            try {
              const singerRes = await musicApi.getSingerBaseInfo(aid);
              singerCache[aid] = singerRes?.data?.passed ? singerRes.data.data?.name || '' : '';
            } catch (e) { singerCache[aid] = ''; }
          }
          singerName = singerCache[aid];
        }

        enriched.push({
          songId: rec.songId,
          similarity: rec.similarity,
          title: song?.title || `歌曲 #${rec.songId}`,
          singerName,
          coverUrl: song?.coverUrl || song?.pic || '',
        });
      }
      return enriched;
    },
    async loadRecommendations(userId, force = false) {
      try {
        const res = await recommendApi.getDailyRecommendations(userId, 10, force);
        if (res?.data?.passed && Array.isArray(res.data.data)) {
          this.dailyRecommendations = await this.enrichRecommendations(res.data.data);
        }
      } catch (e) { /* silent */ }
    },
    async loadCFRecommendations(userId, force = false) {
      this.cfLoading = true;
      this.cfError = '';
      this.cfMessage = '';
      try {
        const res = await recommendApi.getItemCFRecommendations(userId, 10, force);
        if (res?.data?.passed && Array.isArray(res.data.data)) {
          this.cfRecommendations = await this.enrichRecommendations(res.data.data);
          if (!this.cfRecommendations.length) {
            this.cfMessage = res.data.message || '协同过滤暂时没有可展示的歌曲。';
          }
        } else {
          this.cfRecommendations = [];
          this.cfError = res?.data?.message || '协同过滤推荐暂时不可用。';
        }
      } catch (e) {
        this.cfRecommendations = [];
        this.cfError = e?.response?.data?.message || '协同过滤推荐加载失败。';
      } finally {
        this.cfLoading = false;
      }
    },
    async refreshCFRecommendations(force = false) {
      let userBase = {};
      try { userBase = JSON.parse(localStorage.getItem('userBase') || '{}'); } catch (e) { /* ignore */ }
      if (!userBase.id) return;
      await this.loadCFRecommendations(userBase.id, force);
    },
    async rebuildCFMatrix() {
      if (this.cfRebuilding) return;
      this.cfRebuilding = true;
      this.cfError = '';
      this.cfMessage = '正在重建协同过滤矩阵，请稍候。';
      try {
        const res = await recommendApi.rebuildItemCFMatrix();
        if (res?.data?.passed) {
          const count = res.data.data?.songsWithSimilarity;
          this.cfMessage = count !== undefined
            ? `协同过滤矩阵已重建，覆盖 ${count} 首歌曲。`
            : (res.data.message || '协同过滤矩阵已重建。');
          await this.refreshCFRecommendations();
        } else {
          this.cfError = res?.data?.message || '协同过滤矩阵重建失败。';
        }
      } catch (e) {
        this.cfError = e?.response?.data?.message || '协同过滤矩阵重建失败。';
      } finally {
        this.cfRebuilding = false;
      }
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
        await this.loadRecommendations(userBase.id, true);
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
    async loadRandomSongs() {
      this.randomLoading = true;
      try {
        const res = await musicApi.getRandomSongs(20);
        if (res.data?.passed) {
          this.randomSongs = res.data.data || [];
        }
      } catch (e) { /* silent */ }
      this.randomLoading = false;
    },
    async refreshRandom() {
      await this.loadRandomSongs();
    },
    playRandomSong(song, idx) {
      const songIds = this.randomSongs.map(s => s.id);
      this.$bus.emit('playSong', {
        songId: song.id,
        playlist: songIds,
        index: idx,
        source: 'random',
      });
    },
    playAllRandom() {
      if (!this.randomSongs.length) return;
      const songIds = this.randomSongs.map(s => s.id);
      this.$bus.emit('playSong', {
        songId: songIds[0],
        playlist: songIds,
        index: 0,
        source: 'random',
      });
    },
    async saveRandomAsPlaylist() {
      let userBase = {};
      try { userBase = JSON.parse(localStorage.getItem('userBase') || '{}'); } catch (e) { /* ignore */ }
      const userId = userBase.id;
      if (!userId) { alert('请先登录'); return; }
      this.randomSaving = true;
      try {
        const createRes = await musicApi.createPlaylist({
          id: userId,
          name: '随机漫游 · ' + new Date().toLocaleDateString('zh-CN'),
          description: '由随机推荐生成的歌单',
        });
        if (createRes.data?.passed) {
          const playlistId = createRes.data.data?.id || createRes.data.data;
          for (const song of this.randomSongs) {
            await musicApi.managePlaylistSong({
              playlistId,
              songId: song.id,
              action: 'add',
            });
          }
          alert('歌单保存成功！');
        }
      } catch (e) {
        alert('保存失败：' + (e.message || '未知错误'));
      }
      this.randomSaving = false;
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

.ghost:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  transform: none;
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
  cursor: pointer;
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

.playlist-cover-row {
  margin: 8px 0;
}

.playlist-thumb {
  width: 100%;
  height: 120px;
  object-fit: cover;
  border-radius: 12px;
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
  overflow: hidden;
}

.video-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  position: absolute;
  inset: 0;
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

.recommend-placeholder {
  padding: 18px 0 4px;
  color: rgba(236, 242, 255, 0.76);
}

.recommend-placeholder p {
  margin: 0;
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

.random-panel {
  background: linear-gradient(135deg, rgba(251, 146, 60, 0.1), rgba(245, 158, 11, 0.08));
  border-color: rgba(251, 146, 60, 0.2);
}

.random-rank {
  background: linear-gradient(135deg, #fb923c, #f59e0b);
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
