<template>
  <div class="singers-container">
    <!-- 搜索栏，占位即可 -->
    <div class="search-bar">
      <input type="text" placeholder="歌手名称、地区等（暂不支持搜索）" disabled />
    </div>

    <!-- 推荐歌手，占位 -->
    <section class="section section-recommend">
      <h2>推荐歌手</h2>
      <p class="placeholder-text">推荐歌手模块还在路上，先从全部里挑几个喜欢的吧～</p>
    </section>

    <!-- 全部歌手：这里放原来的内容 -->
    <section class="section section-more">
      <h2>全部歌手</h2>
      <div class="singers-list">
        <div
          v-for="singer in singers"
          :key="singer.id"
          class="singer-card"
          @click="goToSinger(singer.id)"
        >
          <img :src="singer.avatarUrl || defaultAvatar" class="singer-avatar" alt="歌手头像" />
          <div class="singer-info">
            <h3>{{ singer.name || '未知歌手' }}</h3>
            <p>{{ singer.genre || '未知风格' }} | {{ singer.country || '未知地区' }}</p>
          </div>
        </div>
      </div>
    </section>
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
        const response = await musicApi.getSingers();
        if (response.data && response.data.passed) {
          this.singers = response.data.data || [];
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('获取歌手列表失败：' + msg);
        }
      } catch (error) {
        alert('获取歌手列表失败：' + error.message);
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
