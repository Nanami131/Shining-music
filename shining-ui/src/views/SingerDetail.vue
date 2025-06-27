<template>
  <div class="singer-detail-container">
    <h2>{{ singer.name || '未知歌手' }}</h2>
    <div class="singer-content">
      <img :src="singer.avatarUrl || defaultAvatar" class="singer-avatar" alt="歌手头像" />
      <div class="singer-info">
        <p><strong>流派：</strong>{{ singer.genre || '未知' }}</p>
        <p><strong>地区：</strong>{{ singer.country || '未知' }}</p>
        <p><strong>简介：</strong>{{ singer.profile || '无简介' }}</p>
        <p><strong>状态：</strong>{{ singer.status === 0 ? '活跃' : '退役' }}</p>
        <p><strong>性别：</strong>{{ singer.sex === 0 ? '男' : singer.sex === 1 ? '女' : '其他' }}</p>
      </div>
    </div>
    <h3>歌曲列表</h3>
    <div class="songs-list">
      <div v-for="song in singer.songs" :key="song.id" class="song-card">
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
import defaultAvatar from '@/assets/default-avatar.png';
import defaultCover from '@/assets/default-cover.png';

export default {
  name: 'SingerDetail',
  data() {
    return {
      singer: {},
      defaultAvatar,
      defaultCover,
    };
  },
  created() {
    this.loadSingerDetails();
  },
  methods: {
    async loadSingerDetails() {
      try {
        const singerId = this.$route.params.id;
        const response = await musicApi.getSingerDetailsInfo(singerId);
        if (response.data.passed) {
          this.singer = response.data.data;
        } else {
          alert('获取歌手详情失败：' + response.data.message);
        }
      } catch (error) {
        alert('获取歌手详情出错：' + error.message);
      }
    },
  },
};
</script>

<style scoped>
.singer-detail-container {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
  background: linear-gradient(to bottom, #e0f7fa, #ffffff);
}
h2 {
  text-align: center;
  margin-bottom: 20px;
}
.singer-content {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
}
.singer-avatar {
  width: 200px;
  height: 200px;
  object-fit: cover;
  border-radius: 8px;
}
.singer-info p {
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