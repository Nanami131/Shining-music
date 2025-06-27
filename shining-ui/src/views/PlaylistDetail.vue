<template>
  <div class="playlist-detail-container">
    <h2>{{ playlist.name || '未知歌单' }}</h2>
    <div class="playlist-content">
      <img :src="playlist.coverUrl || defaultCover" class="playlist-cover" alt="歌单封面" />
      <div class="playlist-info">
        <p><strong>描述：</strong>{{ playlist.description || '无描述' }}</p>
        <p><strong>类型：</strong>{{ playlist.type === 0 ? '普通' : playlist.type === 1 ? '收藏' : '专辑' }}</p>
        <p><strong>可见性：</strong>{{ playlist.visibility === 0 ? '公开' : '私有' }}</p>
        <p><strong>创建时间：</strong>{{ playlist.createdAt || '未知' }}</p>
      </div>
    </div>
    <h3>歌曲列表</h3>
    <div class="songs-list">
      <div v-for="song in playlist.songs" :key="song.id" class="song-card">
        <img :src="song.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" />
        <div class="song-info">
          <h4>{{ song.title || '未知歌曲' }}</h4>
        </div>
      </div>
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
      playlist: {},
      defaultCover,
    };
  },
  created() {
    this.loadPlaylistDetails();
  },
  methods: {
    async loadPlaylistDetails() {
      try {
        const playlistId = this.$route.params.id;
        const response = await musicApi.getPlaylistDetailsInfo(playlistId);
        if (response.data.passed) {
          this.playlist = response.data.data;
        } else {
          alert('获取歌单详情失败：' + response.data.message);
        }
      } catch (error) {
        alert('获取歌单详情出错：' + error.message);
      }
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