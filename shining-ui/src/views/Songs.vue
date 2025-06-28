<template>
  <div class="songs-container">
    <h2>发现歌曲</h2>
    <div class="songs-list">
      <div v-for="song in songs" :key="song.id" class="song-card" @click="goToSong(song.id)">
        <img :src="song.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" />
        <div class="song-info">
          <h3>{{ song.title || '未知歌曲' }}</h3>
          <p>歌手 ID: {{ song.artistId || '未知' }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import musicApi from '@/api/music';
import defaultCover from '@/assets/default-cover.png';

export default {
  name: 'Songs',
  data() {
    return {
      songs: [],
      defaultCover,
    };
  },
  created() {
    this.loadSongs();
  },
  methods: {
    async loadSongs() {
      try {
        const songPromises = [];
        for (let id = 1; id <= 20; id++) {
          songPromises.push(musicApi.getSongBaseInfo(id).catch(() => null));
        }
        const responses = await Promise.all(songPromises);
        this.songs = responses
            .filter(response => response && response.data.passed)
            .map(response => response.data.data);
      } catch (error) {
        alert('获取歌曲列表出错：' + error.message);
      }
    },
    goToSong(songId) {
      this.$router.push(`/song/${songId}`);
    },
  },
};
</script>

<style scoped>
.songs-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  background: linear-gradient(to bottom, #e0f7fa, #ffffff);
}
h2 {
  text-align: center;
  margin-bottom: 20px;
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
.song-info h3 {
  margin: 0;
  font-size: 18px;
}
.song-info p {
  margin: 5px 0 0;
  color: #666;
  font-size: 14px;
}
</style>