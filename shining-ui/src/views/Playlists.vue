<template>
  <div class="playlists-container">
    <!-- 搜索占位 + 创建按钮 -->
    <div class="top-bar">
      <div class="search-bar">
        <input type="text" placeholder="歌单名、简介等（暂不支持搜索）" disabled />
      </div>
      <button
        v-if="userId"
        class="btn primary create-toggle-btn"
        @click="toggleCreatePanel"
      >
        {{ showCreatePanel ? '收起创建歌单' : '创建歌单' }}
      </button>
    </div>

    <!-- 创建歌单，仅在点击按钮后展示 -->
    <section class="section section-create" v-if="userId && showCreatePanel">
      <h2>创建歌单</h2>
      <div class="create-form">
        <div class="field-row">
          <input
            v-model="newPlaylistName"
            type="text"
            placeholder="歌单名称（必填）"
          />
        </div>
        <div class="field-row">
          <textarea
            v-model="newPlaylistDescription"
            placeholder="简介（可选）"
          ></textarea>
        </div>
        <div class="field-row favorites-actions">
          <button class="btn" @click="toggleFavoriteSelector" :disabled="loadingFavorites">
            {{ showFavoriteSelector ? '收起收藏歌曲' : '从我的收藏中选择歌曲' }}
          </button>
          <button
            v-if="showFavoriteSelector && favoriteSongsForCreate.length"
            class="btn link"
            @click="toggleSelectAllFavorites"
          >
            {{ isAllFavoritesSelected ? '取消全选' : '全选收藏歌曲' }}
          </button>
          <span class="selected-count" v-if="selectedFavoriteSongIds.length">
            已选 {{ selectedFavoriteSongIds.length }} 首
          </span>
        </div>
      </div>

      <div v-if="showFavoriteSelector" class="favorite-selector">
        <div v-if="loadingFavorites" class="placeholder-text">
          正在加载我的收藏...
        </div>
        <div v-else-if="!favoriteSongsForCreate.length" class="placeholder-text">
          你还没有收藏任何歌曲，先去收藏几首吧～
        </div>
        <div v-else class="favorite-songs-list">
          <label
            v-for="song in favoriteSongsForCreate"
            :key="song.id"
            class="favorite-item"
          >
            <input
              type="checkbox"
              :value="song.id"
              v-model="selectedFavoriteSongIds"
            />
            <img :src="song.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" />
            <div class="song-info">
              <div class="title">{{ song.title || '未知歌曲' }}</div>
              <div class="sub">歌手 ID: {{ song.artistId || '未知' }}</div>
            </div>
          </label>
        </div>
      </div>

      <div class="create-actions">
        <button class="btn primary" @click="createPlaylist" :disabled="creating">
          {{ creating ? '创建中...' : '创建歌单' }}
        </button>
      </div>
    </section>

    <!-- 推荐歌单占位 -->
    <section class="section section-recommend">
      <h2>推荐歌单</h2>
      <p class="placeholder-text">推荐歌单模块还在路上，先看看下面的全部歌单吧～</p>
    </section>

    <!-- 全部歌单 -->
    <section class="section section-more">
      <h2>全部歌单</h2>
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
            <p>{{ playlist.description || '暂无简介' }}</p>
            <p class="creator" v-if="playlist.nickName || playlist.userId !== undefined">
              创建者：{{ getCreatorName(playlist) }}
            </p>
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
      // 创建歌单相关
      newPlaylistName: '',
      newPlaylistDescription: '',
      favoriteSongsForCreate: [],
      selectedFavoriteSongIds: [],
      showFavoriteSelector: false,
      loadingFavorites: false,
      creating: false,
      showCreatePanel: false,
    };
  },
  computed: {
    isAllFavoritesSelected() {
      return (
        this.favoriteSongsForCreate.length > 0 &&
        this.selectedFavoriteSongIds.length === this.favoriteSongsForCreate.length
      );
    },
  },
  created() {
    const userBase = JSON.parse(localStorage.getItem('userBase') || '{}');
    this.userId = userBase.id ?? null;
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
    async loadFavoriteSongsForCreate() {
      if (!this.userId) {
        alert('请先登录再创建歌单');
        return;
      }
      this.loadingFavorites = true;
      try {
        const response = await musicApi.getUserFavoriteSongs(this.userId);
        if (response.data && response.data.passed) {
          this.favoriteSongsForCreate = response.data.data || [];
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          alert('获取收藏歌曲失败：' + msg);
        }
      } catch (error) {
        alert('获取收藏歌曲失败：' + error.message);
      } finally {
        this.loadingFavorites = false;
      }
    },
    toggleCreatePanel() {
      this.showCreatePanel = !this.showCreatePanel;
      if (!this.showCreatePanel) {
        this.resetCreateForm();
      }
    },
    toggleFavoriteSelector() {
      if (!this.showFavoriteSelector && !this.favoriteSongsForCreate.length) {
        this.loadFavoriteSongsForCreate();
      }
      this.showFavoriteSelector = !this.showFavoriteSelector;
    },
    toggleSelectAllFavorites() {
      if (this.isAllFavoritesSelected) {
        this.selectedFavoriteSongIds = [];
      } else {
        this.selectedFavoriteSongIds = this.favoriteSongsForCreate.map(song => song.id);
      }
    },
    async createPlaylist() {
      if (!this.userId) {
        alert('请先登录再创建歌单');
        return;
      }
      if (!this.newPlaylistName || !this.newPlaylistName.trim()) {
        alert('歌单名称不能为空');
        return;
      }
      this.creating = true;
      try {
        const payload = {
          id: this.userId,
          name: this.newPlaylistName.trim(),
          description: this.newPlaylistDescription || '',
          // 1: 普通歌单
          type: 1,
          // 0: 公共
          visibility: 0,
        };
        const response = await musicApi.createPlaylist(payload);
        if (!response.data || !response.data.passed) {
          const msg = response.data ? response.data.message : '未知错误';
          alert('创建歌单失败：' + msg);
          return;
        }

        // 重新加载歌单列表，便于拿到新建歌单的 ID
        await this.loadPlaylists();

        // 尝试根据名称和 userId 找到刚创建的歌单
        let playlistId = null;
        const candidates = this.playlists.filter(
          p => p.name === payload.name && p.userId === this.userId
        );
        if (candidates.length === 1) {
          playlistId = candidates[0].id;
        } else if (candidates.length > 1) {
          playlistId = candidates.reduce((max, p) => (p.id > max.id ? p : max)).id;
        }

        if (!playlistId) {
          alert('歌单已创建，但暂时无法自动加入收藏的歌曲');
          this.resetCreateForm();
          this.showCreatePanel = false;
          return;
        }

        // 把勾选的收藏歌曲加入新歌单
        if (this.selectedFavoriteSongIds.length) {
          for (const songId of this.selectedFavoriteSongIds) {
            try {
              await musicApi.managePlaylistSong({ playlistId, songId });
            } catch (e) {
              // 单首失败不影响整体
              console.error('添加歌曲到歌单失败', e);
            }
          }
        }

        this.resetCreateForm();
        this.showCreatePanel = false;
        await this.loadPlaylists();
        alert('创建歌单成功');
      } catch (error) {
        alert('创建歌单异常：' + error.message);
      } finally {
        this.creating = false;
      }
    },
    resetCreateForm() {
      this.newPlaylistName = '';
      this.newPlaylistDescription = '';
      this.selectedFavoriteSongIds = [];
      this.showFavoriteSelector = false;
    },
    getCreatorName(playlist) {
      if (!playlist) {
        return '未知用户';
      }
      if (playlist.userId === -1) {
        return '官方';
      }
      if (playlist.nickName) {
        return playlist.nickName;
      }
      return playlist.userId ? `用户${playlist.userId}` : '未知用户';
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

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.search-bar {
  flex: 1;
  max-width: 400px;
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

.section-create .create-form {
  background: rgba(255, 255, 255, 0.9);
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
  margin-bottom: 12px;
}

.field-row {
  margin-bottom: 10px;
}

.field-row input,
.field-row textarea {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  font-size: 14px;
  resize: vertical;
}

.favorites-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.selected-count {
  font-size: 13px;
  color: #64748b;
}

.favorite-selector {
  margin-top: 8px;
  margin-bottom: 12px;
  max-height: 260px;
  overflow-y: auto;
  padding: 10px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
}

.favorite-songs-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.favorite-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px;
  border-radius: 8px;
  cursor: pointer;
}

.favorite-item:hover {
  background: #f1f5f9;
}

.favorite-item input[type='checkbox'] {
  margin: 0;
}

.favorite-item .song-cover {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  object-fit: cover;
}

.favorite-item .song-info .title {
  font-size: 14px;
}

.favorite-item .song-info .sub {
  font-size: 12px;
  color: #64748b;
}

.create-actions {
  margin-top: 4px;
}

.btn {
  padding: 6px 14px;
  border-radius: 999px;
  border: none;
  background: #e2e8f0;
  color: #1e293b;
  font-size: 13px;
  cursor: pointer;
}

.btn.primary {
  background: linear-gradient(120deg, #4facfe, #00f2fe);
  color: #fff;
}

.btn.link {
  background: transparent;
  color: #2563eb;
}

.btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.create-toggle-btn {
  white-space: nowrap;
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

