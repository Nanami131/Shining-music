<template>
  <div class="singers-container">
    <h2>发现歌手</h2>
    <div class="singers-list">
      <div v-for="singer in singers" :key="singer.id" class="singer-card" @click="goToSinger(singer.id)">
        <img :src="singer.avatarUrl || defaultAvatar" class="singer-avatar" alt="歌手头像" />
        <div class="singer-info">
          <h3>{{ singer.name || '未知歌手' }}</h3>
          <p>{{ singer.genre || '未知流派' }} | {{ singer.country || '未知地区' }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import musicApi from '@/api/music';
import defaultAvatar from '@/assets/default-avatar.png';

export default {
  name: 'Singers',
  data() {
    return {
      singers: [],
      defaultAvatar,
    };
  },
  created() {
    this.loadSingers();
  },
  methods: {
    async loadSingers() {
      try {
        const singerPromises = [];
        for (let id = 1; id <= 20; id++) {
          singerPromises.push(musicApi.getSingerBaseInfo(id).catch(() => null));
        }
        const responses = await Promise.all(singerPromises);
        this.singers = responses
            .filter(response => response && response.data.passed)
            .map(response => response.data.data);
      } catch (error) {
        alert('获取歌手列表出错：' + error.message);
      }
    },
    goToSinger(singerId) {
      this.$router.push(`/singer/${singerId}`);
    },
  },
};
</script>

<style scoped>
.singers-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  background: linear-gradient(to bottom, #e0f7fa, #ffffff);
}
h2 {
  text-align: center;
  margin-bottom: 20px;
}
.singers-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 20px;
}
.singer-card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  padding: 15px;
  cursor: pointer;
  transition: transform 0.2s;
}
.singer-card:hover {
  transform: scale(1.05);
}
.singer-avatar {
  width: 100%;
  height: 150px;
  object-fit: cover;
  border-radius: 8px;
  margin-bottom: 10px;
}
.singer-info h3 {
  margin: 0;
  font-size: 18px;
}
.singer-info p {
  margin: 5px 0 0;
  color: #666;
  font-size: 14px;
}
</style>