<template>
  <div class="my-music-container">
    <h2>我的音乐</h2>
    <div v-if="!userId" class="empty-tip">
      请先登录后查看收藏歌曲。
    </div>
    <div v-else>
      <section class="favorites-section">
        <div class="section-header">
          <h3>我的收藏</h3>
          <button class="refresh-btn" @click="loadFavorites" :disabled="loading">
            {{ loading ? '加载中…' : '刷新' }}
          </button>
        </div>
        <div v-if="loading" class="empty-tip">正在加载收藏歌曲，请稍候…</div>
        <div v-else-if="favorites.length === 0" class="empty-tip">还没有收藏任何歌曲</div>
        <div v-else class="songs-list">
          <div
            v-for="song in favorites"
            :key="song.id"
            class="song-card"
            @click="goToSong(song.id)"
          >
            <img :src="song.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" />
            <div class="song-info">
              <h3>{{ song.title || '未知歌曲' }}</h3>
              <p>歌手 ID: {{ song.artistId || '未知' }}</p>
            </div>
            <button
              class="favorite-btn"
              :class="{ active: song.favorite }"
              @click.stop="toggleFavorite(song)"
              title="取消收藏"
            >
              <span class="heart-icon"></span>
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script>
import musicApi from '@/api/music';
import defaultCover from '@/assets/default-cover.png';

export default {
  name: 'MyMusic',
  data() {
    return {
      favorites: [],
      loading: false,
      userId: null,
      defaultCover,
    };
  },
  created() {
    const userBase = JSON.parse(localStorage.getItem('userBase') || '{}');
    this.userId = userBase.id ?? null;
    if (this.userId) {
      this.loadFavorites();
    }
  },
  methods: {
    async loadFavorites() {
      if (!this.userId) {
        return;
      }
      this.loading = true;
      try {
        const response = await musicApi.getUserFavoriteSongs(this.userId);
        if (response.data && response.data.passed) {
          this.favorites = response.data.data || [];
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('获取收藏歌曲失败：' + msg);
        }
      } catch (error) {
        alert('获取收藏歌曲失败：' + error.message);
      } finally {
        this.loading = false;
      }
    },
    async toggleFavorite(song) {
      if (!song || !song.id) {
        return;
      }
      if (!this.userId) {
        alert('请先登录后再收藏歌曲');
        return;
      }
      try {
        const response = await musicApi.toggleFavoriteSong({
          userId: this.userId,
          songId: song.id,
        });
        if (response.data && response.data.passed) {
          const favorite = response.data.data?.favorite ?? false;
          song.favorite = favorite;
          if (!favorite) {
            this.favorites = this.favorites.filter(item => item.id !== song.id);
          }
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('更新收藏状态失败：' + msg);
        }
      } catch (error) {
        alert('更新收藏状态失败：' + error.message);
      }
    },
    goToSong(songId) {
      this.$router.push(`/song/${songId}`);
    },
  },
};
</script>

<style scoped>
.my-music-container {
  padding: 24px;
  max-width: 1100px;
  margin: 0 auto;
  background: linear-gradient(to bottom, #e0f7fa, #ffffff);
}
h2 {
  margin-bottom: 20px;
  text-align: center;
}
.favorites-section {
  background: rgba(255, 255, 255, 0.85);
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 10px 25px rgba(15, 23, 42, 0.08);
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.refresh-btn {
  padding: 6px 16px;
  border-radius: 999px;
  border: none;
  background: linear-gradient(120deg, #4facfe, #00f2fe);
  color: #fff;
  cursor: pointer;
  font-size: 14px;
}
.refresh-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.empty-tip {
  text-align: center;
  color: #64748b;
  padding: 40px 0;
}
.songs-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 20px;
}
.song-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
  padding: 16px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  position: relative;
}
.song-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 26px rgba(15, 23, 42, 0.12);
}
.song-cover {
  width: 100%;
  height: 150px;
  object-fit: cover;
  border-radius: 10px;
  margin-bottom: 12px;
}
.song-info h3 {
  margin: 0;
  font-size: 18px;
}
.song-info p {
  margin: 4px 0 0;
  color: #666;
  font-size: 14px;
}
.favorite-btn {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 42px;
  height: 42px;
  border-radius: 21px;
  border: none;
  background: rgba(255, 255, 255, 0.8);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s, box-shadow 0.2s, background 0.2s;
}
.favorite-btn .heart-icon {
  width: 22px;
  height: 22px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23ff6b81' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M12 21s-6.3-4.35-9-8.4C1 9 2 5 5.5 4S12 8 12 8s2.5-4 6-4 4.5 3 2.5 6.6c-2.7 4.05-9 8.4-9 8.4z'/%3E%3C/svg%3E");
  background-size: contain;
  background-repeat: no-repeat;
}
.favorite-btn.active {
  background: rgba(255, 99, 132, 0.18);
  box-shadow: 0 6px 16px rgba(255, 99, 132, 0.35);
}
.favorite-btn.active .heart-icon {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23ff3366'%3E%3Cpath d='M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3c3.08 0 5.5 2.42 5.5 5.5 0 3.78-3.4 6.86-8.55 11.54L12 21.35z'/%3E%3C/svg%3E");
}
.favorite-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 20px rgba(255, 99, 132, 0.3);
}
</style>
