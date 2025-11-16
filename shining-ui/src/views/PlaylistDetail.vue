<template>
  <div class="playlist-detail-container">
    <div v-if="isLoaded">
      <h2>{{ playlist.name || '未知歌单' }}</h2>
      <div class="playlist-content">
        <img :src="playlist.coverUrl || defaultCover" class="playlist-cover" alt="歌单封面" />
        <div class="playlist-info">
          <p><strong>简介：</strong>{{ playlist.description || '暂无简介' }}</p>
          <p v-if="playlist.nickName || playlist.userId"><strong>创建者：</strong>{{ creatorName }}</p>
          <p><strong>类型：</strong>{{ formatType(playlist.type) }}</p>
          <p><strong>可见性：</strong>{{ formatVisibility(playlist.visibility) }}</p>
          <p><strong>创建时间：</strong>{{ playlist.createdAt || '未知' }}</p>
        </div>
      </div>
      <h3>歌曲列表</h3>
      <div class="songs-list">
        <div
          v-for="song in playlist.songs"
          :key="song.id"
          class="song-card"
          @click="goToSong(song.id)"
        >
          <img :src="song.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" />
          <div class="song-info">
            <h4>{{ song.title || '未知歌曲' }}</h4>
          </div>
        </div>
      </div>
    </div>
    <div v-else-if="hasError">
      <h2>歌单加载失败</h2>
      <p>请稍后再试。</p>
    </div>
  </div>
</template>

<script>
import musicApi from '@/api/music';
import defaultCover from '@/assets/default-cover.png';

export default {
  name: 'PlaylistDetail',
  data() {
    return {
      playlist: null,
      defaultCover,
      isLoaded: false,
      hasError: false,
    };
  },
  computed: {
    creatorName() {
      if (!this.playlist) {
        return '未知用户';
      }
      if (this.playlist.userId === -1) {
        return '官方';
      }
      if (this.playlist.nickName) {
        return this.playlist.nickName;
      }
      return this.playlist.userId ? `用户${this.playlist.userId}` : '未知用户';
    },
  },
  created() {
    this.loadPlaylistDetails();
  },
  methods: {
    async loadPlaylistDetails() {
      this.isLoaded = false;
      this.hasError = false;
      try {
        const playlistId = this.$route.params.id;
        const response = await musicApi.getPlaylistDetailsInfo(playlistId);
        if (response.data.passed) {
          this.playlist = response.data.data;
          this.isLoaded = true;
        } else {
          this.hasError = true;
          alert('获取歌单详情失败：' + response.data.message);
        }
      } catch (error) {
        this.hasError = true;
        alert('获取歌单详情异常：' + error.message);
      }
    },
    formatType(type) {
      if (type === 0) return '普通';
      if (type === 1) return '收藏';
      if (type === 2) return '专辑';
      return '未知';
    },
    formatVisibility(visibility) {
      if (visibility === 0) return '公开';
      if (visibility === 1) return '私密';
      return '未知';
    },
    goToSong(songId) {
      this.$router.push(`/song/${songId}`);
    },
  },
};
</script>

<style scoped>
.playlist-detail-container {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
  background: linear-gradient(to bottom, #e0f7fa, #ffffff);
}
h2 {
  text-align: center;
  margin-bottom: 20px;
}
.playlist-content {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
}
.playlist-cover {
  width: 200px;
  height: 200px;
  object-fit: cover;
  border-radius: 8px;
}
.playlist-info p {
  margin: 10px 0;
  font-size: 16px;
}
h3 {
  margin: 20px 0 10px;
}
.songs-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 20px;
}
.song-card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  padding: 15px;
  cursor: pointer;
  transition: transform 0.2s;
}
.song-card:hover {
  transform: scale(1.05);
}
.song-cover {
  width: 100%;
  height: 150px;
  object-fit: cover;
  border-radius: 8px;
  margin-bottom: 10px;
}
.song-info h4 {
  margin: 0;
  font-size: 16px;
}
</style>
