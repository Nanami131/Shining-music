<template>
  <div class="playlists-container">
    <h2>发现歌单</h2>
    <div class="playlists-list">
      <div v-for="playlist in playlists" :key="playlist.id" class="playlist-card" @click="goToPlaylist(playlist.id)">
        <img :src="playlist.coverUrl || defaultCover" class="playlist-cover" alt="歌单封面" />
        <div class="playlist-info">
          <h3>{{ playlist.name || '未知歌单' }}</h3>
          <p>{{ playlist.description || '无描述' }}</p>
        </div>
      </div>
    </div>
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
      defaultCover,
    };
  },
  created() {
    this.loadPlaylists();
  },
  methods: {
    async loadPlaylists() {
      try {
        const playlistPromises = [];
        for (let id = 1; id <= 20; id++) {
          playlistPromises.push(musicApi.getPlaylistBaseInfo(id).catch(() => null));
        }
        const responses = await Promise.all(playlistPromises);
        this.playlists = responses
            .filter(response => response && response.data.passed)
            .map(response => response.data.data);
      } catch (error) {
        alert('获取歌单列表出错：' + error.message);
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
h2 {
  text-align: center;
  margin-bottom: 20px;
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
  margin: 5px 0 0;
  color: #666;
  font-size: 14px;
}
</style>