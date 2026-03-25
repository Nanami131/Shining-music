<template>
  <div class="playlist-detail-container">
    <div v-if="isLoaded && playlist">
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

      <section v-if="isOwner" class="editor-section">
        <h3>歌单信息编辑</h3>
        <div class="editor-row">
          <label>名称</label>
          <input v-model="editForm.name" type="text" placeholder="歌单名称" />
        </div>
        <div class="editor-row">
          <label>简介</label>
          <textarea v-model="editForm.description" placeholder="歌单简介"></textarea>
        </div>
        <div class="editor-actions">
          <button class="btn primary" :disabled="updatingInfo" @click="updatePlaylistInfo">
            {{ updatingInfo ? '保存中...' : '保存歌单信息' }}
          </button>
        </div>

        <h3>歌单封面编辑</h3>
        <div class="editor-row">
          <input type="file" accept="image/*" @change="onCoverChange" />
        </div>
        <div class="editor-actions">
          <button class="btn primary" :disabled="uploadingCover || !newCoverFile" @click="uploadCover">
            {{ uploadingCover ? '上传中...' : '上传新封面' }}
          </button>
        </div>
      </section>

      <section>
        <h3>歌曲列表</h3>
        <div class="editor-actions">
          <button class="btn primary" :disabled="songOperating || !playlistSongs.length" @click="playAllSongs">
            播放全部
          </button>
        </div>
        <div class="songs-list">
          <div v-for="song in playlistSongs" :key="song.id" class="song-card">
            <img :src="song.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" @click="goToSong(song.id)" />
            <div class="song-info">
              <h4 @click="goToSong(song.id)">{{ song.title || '未知歌曲' }}</h4>
            </div>
            <button class="btn primary mini" :disabled="songOperating" @click.stop="playSingleSong(song.id)">
              播放
            </button>
            <button v-if="isOwner" class="btn danger mini" :disabled="songOperating" @click.stop="removeSong(song.id)">
              移除
            </button>
          </div>
        </div>
      </section>

      <section v-if="isOwner" class="editor-section">
        <h3>添加歌曲到歌单</h3>
        <div class="editor-row favorites-actions">
          <button class="btn" @click="toggleSongSelector">
            {{ showSongSelector ? '收起可选歌曲' : '从我喜欢的歌曲选择' }}
          </button>
          <button
            v-if="showSongSelector && availableSongs.length"
            class="btn link"
            @click="toggleSelectAllSongs"
          >
            {{ isAllAvailableSelected ? '取消全选' : '全选歌曲' }}
          </button>
          <span class="selected-count" v-if="selectedSongIds.length">
            已选 {{ selectedSongIds.length }} 首
          </span>
        </div>

        <div v-if="showSongSelector" class="favorite-selector">
          <div v-if="!availableSongs.length" class="placeholder-text">
            暂无可添加歌曲
          </div>
          <div v-else class="favorite-songs-list">
            <label
              v-for="song in availableSongs"
              :key="song.id"
              class="favorite-item"
            >
              <input
                type="checkbox"
                :value="song.id"
                v-model="selectedSongIds"
              />
              <img :src="song.coverUrl || defaultCover" class="song-cover-mini" alt="歌曲封面" />
              <div class="song-info">
                <div class="title">{{ song.title || '未知歌曲' }}</div>
                <div class="sub">歌曲 ID: {{ song.id }}</div>
              </div>
            </label>
          </div>
        </div>

        <div class="editor-actions">
          <button class="btn primary" :disabled="songOperating || !selectedSongIds.length" @click="addSongsToPlaylist">
            {{ songOperating ? '添加中...' : '添加已选歌曲' }}
          </button>
        </div>
      </section>

      <section v-if="isOwner" class="editor-section danger-zone">
        <h3>危险操作</h3>
        <button class="btn danger" :disabled="deletingPlaylist" @click="deletePlaylist">
          {{ deletingPlaylist ? '删除中...' : '删除歌单' }}
        </button>
      </section>
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
      favoriteSongs: [],
      selectedSongIds: [],
      showSongSelector: false,
      newCoverFile: null,
      editForm: {
        name: '',
        description: '',
      },
      updatingInfo: false,
      uploadingCover: false,
      songOperating: false,
      deletingPlaylist: false,
      defaultCover,
      isLoaded: false,
      hasError: false,
      userId: null,
    };
  },
  computed: {
    creatorName() {
      if (!this.playlist) return '未知用户';
      if (this.playlist.userId === -1) return '官方';
      if (this.playlist.nickName) return this.playlist.nickName;
      return this.playlist.userId ? `用户${this.playlist.userId}` : '未知用户';
    },
    isOwner() {
      return !!this.userId && !!this.playlist && this.playlist.userId === this.userId;
    },
    playlistSongs() {
      return Array.isArray(this.playlist?.songs) ? this.playlist.songs : [];
    },
    availableSongs() {
      const inPlaylist = new Set(this.playlistSongs.map(song => song.id));
      return (this.favoriteSongs || []).filter(song => !inPlaylist.has(song.id));
    },
    isAllAvailableSelected() {
      return this.availableSongs.length > 0 && this.selectedSongIds.length === this.availableSongs.length;
    },
  },
  created() {
    const userBase = JSON.parse(localStorage.getItem('userBase') || '{}');
    this.userId = userBase.id ?? null;
    this.loadPageData();
  },
  methods: {
    async loadPageData() {
      this.isLoaded = false;
      this.hasError = false;
      try {
        await Promise.all([this.loadPlaylistDetails(), this.loadFavoriteSongs()]);
        this.isLoaded = true;
      } catch (error) {
        this.hasError = true;
        alert('加载页面失败：' + error.message);
      }
    },
    async loadPlaylistDetails() {
      const playlistId = this.$route.params.id;
      const response = await musicApi.getPlaylistDetailsInfo(playlistId);
      if (!response.data?.passed) {
        throw new Error(response.data?.message || '获取歌单详情失败');
      }
      this.playlist = response.data.data;
      this.editForm.name = this.playlist.name || '';
      this.editForm.description = this.playlist.description || '';
    },
    async loadFavoriteSongs() {
      if (!this.userId) {
        this.favoriteSongs = [];
        return;
      }
      const response = await musicApi.getUserFavoriteSongs(this.userId);
      if (response.data?.passed) {
        this.favoriteSongs = response.data.data || [];
      } else {
        this.favoriteSongs = [];
      }
    },
    async updatePlaylistInfo() {
      if (!this.isOwner) {
        alert('只有歌单创建者可以修改');
        return;
      }
      if (!this.editForm.name || !this.editForm.name.trim()) {
        alert('歌单名称不能为空');
        return;
      }
      this.updatingInfo = true;
      try {
        const response = await musicApi.updatePlaylist({
          id: this.playlist.id,
          userId: this.userId,
          name: this.editForm.name.trim(),
          description: this.editForm.description ?? '',
        });
        if (!response.data?.passed) {
          alert('更新失败：' + (response.data?.message || '未知错误'));
          return;
        }
        await this.loadPlaylistDetails();
        alert('歌单信息更新成功');
      } catch (error) {
        alert('更新异常：' + error.message);
      } finally {
        this.updatingInfo = false;
      }
    },
    onCoverChange(event) {
      const file = event.target.files?.[0] || null;
      this.newCoverFile = file;
    },
    async uploadCover() {
      if (!this.isOwner) {
        alert('只有歌单创建者可以修改封面');
        return;
      }
      if (!this.newCoverFile) {
        alert('请先选择图片');
        return;
      }
      this.uploadingCover = true;
      try {
        const response = await musicApi.uploadPlaylistCover(
          this.playlist.id,
          this.newCoverFile,
          String(Date.now())
        );
        if (!response.data?.passed) {
          alert('封面上传失败：' + (response.data?.message || '未知错误'));
          return;
        }
        this.newCoverFile = null;
        await this.loadPlaylistDetails();
        alert('封面上传成功');
      } catch (error) {
        alert('封面上传异常：' + error.message);
      } finally {
        this.uploadingCover = false;
      }
    },
    toggleSongSelector() {
      this.showSongSelector = !this.showSongSelector;
    },
    toggleSelectAllSongs() {
      if (this.isAllAvailableSelected) {
        this.selectedSongIds = [];
      } else {
        this.selectedSongIds = this.availableSongs.map(song => song.id);
      }
    },
    async addSongsToPlaylist() {
      if (!this.isOwner) {
        alert('只有歌单创建者可以编辑歌曲');
        return;
      }
      if (!this.selectedSongIds.length) {
        alert('请先选择歌曲');
        return;
      }
      this.songOperating = true;
      try {
        for (const songId of this.selectedSongIds) {
          const response = await musicApi.managePlaylistSong({
            playlistId: this.playlist.id,
            songId,
          });
          if (!response.data?.passed) {
            alert('添加失败：' + (response.data?.message || '未知错误'));
            return;
          }
        }
        this.selectedSongIds = [];
        this.showSongSelector = false;
        await this.loadPlaylistDetails();
      } catch (error) {
        alert('添加异常：' + error.message);
      } finally {
        this.songOperating = false;
      }
    },
    async removeSong(songId) {
      if (!this.isOwner) {
        alert('只有歌单创建者可以编辑歌曲');
        return;
      }
      this.songOperating = true;
      try {
        const response = await musicApi.managePlaylistSong({
          playlistId: this.playlist.id,
          songId,
        });
        if (!response.data?.passed) {
          alert('移除失败：' + (response.data?.message || '未知错误'));
          return;
        }
        await this.loadPlaylistDetails();
      } catch (error) {
        alert('移除异常：' + error.message);
      } finally {
        this.songOperating = false;
      }
    },
    async playAllSongs() {
      if (!this.playlistSongs.length) {
        alert('当前歌单没有歌曲');
        return;
      }
      this.songOperating = true;
      try {
        if (this.userId) {
          const clearResponse = await musicApi.clearCurrentPlaylist(this.userId);
          if (!clearResponse.data?.passed) {
            alert('清空当前播放列表失败：' + (clearResponse.data?.message || '未知错误'));
            return;
          }
          const currentResponse = await musicApi.getCurrentPlaylist(this.userId);
          if (!currentResponse.data?.passed || !currentResponse.data?.data?.id) {
            alert('获取当前播放列表失败：' + (currentResponse.data?.message || '未知错误'));
            return;
          }
          const currentPlaylistId = currentResponse.data.data.id;
          for (const song of this.playlistSongs) {
            const addResponse = await musicApi.managePlaylistSong({
              playlistId: currentPlaylistId,
              songId: song.id,
            });
            if (!addResponse.data?.passed) {
              alert('加入当前播放列表失败：' + (addResponse.data?.message || '未知错误'));
              return;
            }
          }
          this.$bus.emit('refreshCurrentPlaylist');
        }

        this.$bus.emit('playSong', {
          songId: this.playlistSongs[0].id,
          playlist: this.playlistSongs.map(song => song.id),
          index: 0,
          source: 'playlistDetail',
        });
      } catch (error) {
        alert('播放全部异常：' + error.message);
      } finally {
        this.songOperating = false;
      }
    },
    playSingleSong(songId) {
      if (!songId) {
        return;
      }
      this.$bus.emit('playSong', {
        songId,
        playlist: [songId],
        index: 0,
        source: 'playlistDetail',
      });
    },
    async deletePlaylist() {
      if (!this.isOwner) {
        alert('只有歌单创建者可以删除');
        return;
      }
      if (!window.confirm('确定删除该歌单吗？')) {
        return;
      }
      this.deletingPlaylist = true;
      try {
        const response = await musicApi.deletePlaylist(this.playlist.id);
        if (!response.data?.passed) {
          alert('删除失败：' + (response.data?.message || '未知错误'));
          return;
        }
        alert('删除成功');
        this.$router.push('/playlists');
      } catch (error) {
        alert('删除异常：' + error.message);
      } finally {
        this.deletingPlaylist = false;
      }
    },
    formatType(type) {
      if (type === 1) return '普通';
      if (type === 2) return '专辑';
      if (type === 3) return '收藏';
      if (type === 4) return '当前播放';
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
  max-width: 960px;
  margin: 0 auto;
  background: linear-gradient(to bottom, #e0f7fa, #ffffff);
}

h2 {
  text-align: center;
  margin-bottom: 20px;
}

h3 {
  margin: 20px 0 10px;
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

.editor-section {
  margin: 20px 0;
  padding: 16px;
  background: #ffffff;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.editor-row {
  margin-bottom: 12px;
}

.editor-row label {
  display: block;
  margin-bottom: 6px;
  font-weight: 600;
}

.editor-row input[type='text'],
.editor-row textarea,
.editor-row select {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  font-size: 14px;
}

.editor-row textarea {
  min-height: 90px;
  resize: vertical;
}

.editor-actions {
  display: flex;
  gap: 10px;
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

.song-cover-mini {
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

.songs-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.song-card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  padding: 12px;
}

.song-cover {
  width: 100%;
  height: 140px;
  object-fit: cover;
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
}

.song-info h4 {
  margin: 0 0 8px;
  font-size: 15px;
  cursor: pointer;
}

.btn {
  border: none;
  border-radius: 6px;
  padding: 8px 12px;
  cursor: pointer;
}

.btn.primary {
  background: #0ea5e9;
  color: #fff;
}

.btn.danger {
  background: #ef4444;
  color: #fff;
}

.btn.mini {
  padding: 6px 10px;
  font-size: 12px;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.danger-zone {
  border: 1px solid #fecaca;
}
</style>
