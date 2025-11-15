<template>
  <div class="playlists-container">
    <!-- 搜索栏（装饰用） -->
    <div class="search-bar">
      <input
        type="text"
        placeholder="搜索歌单（暂未开通搜索功能）"
        disabled
      />
    </div>

    <!-- 推荐歌单（占位，内容暂留空） -->
    <section class="section section-recommend">
      <h2>推荐歌单</h2>
      <p class="placeholder-text">推荐歌单功能开发中，敬请期待。</p>
    </section>

    <!-- 更多歌单（真实数据） -->
    <section class="section section-more">
      <h2>更多歌单</h2>
      <div class="playlists-list">
        <div
          v-for="playlist in playlists"
          :key="playlist.id"
          class="playlist-card"
          @click="goToPlaylist(playlist.id)"
        >
          <img
            :src="playlist.coverUrl || defaultCover"
            class="playlist-cover"
            alt="歌单封面"
          />
          <div class="playlist-info">
            <h3>{{ playlist.name || '未知歌单' }}</h3>
            <p>{{ playlist.description || '暂无描述' }}</p>
            <p class="creator" v-if="playlist.userId">创建者：{{ playlist.userId }}</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import musicApi from '@/api/music';
import defaultCover from '@/assets/default-cover.png';

export default {
  name: 'Playlists',
  data() {
    return {
      playlists: [],
      userId: null,
      defaultCover,
    };
  },
  created() {
    const userBase = JSON.parse(localStorage.getItem('userBase') || '{}');
    this.userId = userBase.id || null;
    this.loadPlaylists();
  },
  methods: {
    async loadPlaylists() {
      try {
        const response = await musicApi.discoverPlaylists(this.userId);
        if (response.data && response.data.passed) {
          this.playlists = response.data.data || [];
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('获取歌单列表失败：' + msg);
        }
      } catch (error) {
        alert('获取歌单列表失败：' + error.message);
      }
    },
    goToPlaylist(playlistId) {
      this.$router.push(`/playlist/${playlistId}`);
    },
  },
};
</script>

<style scoped>
.playlists-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  background: linear-gradient(to bottom, #e0f7fa, #ffffff);
}

.search-bar {
  max-width: 400px;
  margin: 0 auto 24px;
}

.search-bar input {
  width: 100%;
  padding: 10px 14px;
  border-radius: 999px;
  border: 1px solid #cbd5e1;
  background-color: #f1f5f9;
  color: #64748b;
  font-size: 14px;
}

.section {
  margin-bottom: 28px;
}

.section h2 {
  margin-bottom: 16px;
  text-align: left;
}

.placeholder-text {
  color: #94a3b8;
  font-size: 14px;
}

.playlists-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 20px;
}

.playlist-card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  padding: 15px;
  cursor: pointer;
  transition: transform 0.2s;
}

.playlist-card:hover {
  transform: scale(1.05);
}

.playlist-cover {
  width: 100%;
  height: 150px;
  object-fit: cover;
  border-radius: 8px;
  margin-bottom: 10px;
}

.playlist-info h3 {
  margin: 0;
  font-size: 18px;
}

.playlist-info p {
  margin: 4px 0 0;
  color: #666;
  font-size: 14px;
}

.creator {
  color: #9ca3af;
  font-size: 12px;
}
</style>

