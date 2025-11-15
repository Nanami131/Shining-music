<template>
  <div class="dev-container">
    <h2>歌单管理（开发模式）</h2>

    <!-- 创建歌单 -->
    <div class="section">
      <h3>创建歌单</h3>
      <form @submit.prevent="handleCreatePlaylist">
        <div class="form-item">
          <label>用户 ID</label>
          <input v-model="createForm.id" type="number" placeholder="请输入用户 ID" required />
        </div>
        <div class="form-item">
          <label>歌单名称</label>
          <input v-model="createForm.name" placeholder="请输入歌单名称" required />
        </div>
        <div class="form-item">
          <label>描述</label>
          <textarea v-model="createForm.description" placeholder="请输入描述"></textarea>
        </div>
        <div class="form-item">
          <label>类型</label>
          <select v-model="createForm.type" required>
            <!-- 普通歌单 -> PLAYLIST = 1 -->
            <option :value="1">普通</option>
            <!-- 收藏歌单 -> USER_FAVORITE = 3 -->
            <option :value="3">收藏</option>
            <!-- 专辑 -> ALBUM = 2 -->
            <option :value="2">专辑</option>
          </select>
        </div>
        <div class="form-item">
          <label>可见性</label>
          <select v-model="createForm.visibility" required>
            <option :value="0">公开</option>
            <option :value="1">私有</option>
          </select>
        </div>
        <button type="submit" class="submit-btn">创建歌单</button>
      </form>
    </div>

    <!-- 上传歌单封面 -->
    <div class="section">
      <h3>上传歌单封面</h3>
      <form @submit.prevent="handleUploadPlaylistCover">
        <div class="form-item">
          <label>歌单 ID</label>
          <input v-model="coverForm.id" type="number" placeholder="请输入歌单 ID" required />
        </div>
        <div class="form-item">
          <label>图片文件</label>
          <input type="file" accept="image/*" @change="handleCoverChange" required />
        </div>
        <button type="submit" class="submit-btn">上传封面</button>
      </form>
    </div>

    <!-- 删除歌单 -->
    <div class="section">
      <h3>删除歌单</h3>
      <form @submit.prevent="handleDeletePlaylist">
        <div class="form-item">
          <label>歌单 ID</label>
          <input v-model="deletePlaylistId" type="number" placeholder="请输入歌单 ID" required />
        </div>
        <button type="submit" class="submit-btn delete-btn">删除歌单</button>
      </form>
    </div>

    <!-- 管理歌单歌曲 -->
    <div class="section">
      <h3>管理歌单歌曲</h3>
      <form @submit.prevent="handleManagePlaylistSong">
        <div class="form-item">
          <label>歌单 ID</label>
          <input v-model="songForm.playlistId" type="number" placeholder="请输入歌单 ID" required />
        </div>
        <div class="form-item">
          <label>歌曲 ID</label>
          <input v-model="songForm.songId" type="number" placeholder="请输入歌曲 ID" required />
        </div>
        <button type="submit" class="submit-btn">添加/移除歌曲</button>
      </form>
    </div>
  </div>
</template>

<script>
import musicApi from '@/api/music';
import md5 from 'js-md5';

export default {
  name: 'PlaylistManage',
  data() {
    return {
      createForm: {
        id: null,
        name: '',
        description: '',
        // 默认普通歌单，对齐后端 Constants.PLAYLIST = 1
        type: 1,
        visibility: 0,
      },
      coverForm: {
        id: null,
        file: null,
        md5: '',
      },
      deletePlaylistId: null,
      songForm: {
        playlistId: null,
        songId: null,
      },
    };
  },
  methods: {
    async handleCreatePlaylist() {
      try {
        const response = await musicApi.createPlaylist(this.createForm);
        if (response.data.passed) {
          alert('创建歌单成功');
          this.createForm = {
            id: null,
            name: '',
            description: '',
            type: 1,
            visibility: 0,
          };
        } else {
          alert('创建歌单失败：' + response.data.message);
        }
      } catch (error) {
        alert('创建歌单异常：' + error.message);
      }
    },
    handleCoverChange(event) {
      const file = event.target.files[0];
      if (file) {
        this.coverForm.file = file;
        const reader = new FileReader();
        reader.onload = () => {
          this.coverForm.md5 = md5(reader.result);
        };
        reader.readAsArrayBuffer(file);
      }
    },
    async handleUploadPlaylistCover() {
      try {
        const response = await musicApi.uploadPlaylistCover(
          this.coverForm.id,
          this.coverForm.file,
          this.coverForm.md5
        );
        if (response.data.passed) {
          alert('上传歌单封面成功');
          this.coverForm = { id: null, file: null, md5: '' };
        } else {
          alert('上传歌单封面失败：' + response.data.message);
        }
      } catch (error) {
        alert('上传歌单封面异常：' + error.message);
      }
    },
    async handleDeletePlaylist() {
      try {
        const response = await musicApi.deletePlaylist(this.deletePlaylistId);
        if (response.data.passed) {
          alert('删除歌单成功');
          this.deletePlaylistId = null;
        } else {
          alert('删除歌单失败：' + response.data.message);
        }
      } catch (error) {
        alert('删除歌单异常：' + error.message);
      }
    },
    async handleManagePlaylistSong() {
      try {
        const response = await musicApi.managePlaylistSong(this.songForm);
        if (response.data.passed) {
          alert('更新歌单歌曲成功');
          this.songForm = { playlistId: null, songId: null };
        } else {
          alert('更新歌单歌曲失败：' + response.data.message);
        }
      } catch (error) {
        alert('更新歌单歌曲异常：' + error.message);
      }
    },
  },
};
</script>

<style scoped>
.dev-container {
  padding: 20px;
  max-width: 600px;
  margin: 0 auto;
  background: #f9fafb;
}
.section {
  margin-bottom: 20px;
  padding: 15px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}
h2 {
  text-align: center;
  margin-bottom: 20px;
}
h3 {
  margin-bottom: 10px;
}
.form-item {
  margin-bottom: 15px;
}
label {
  display: block;
  margin-bottom: 5px;
}
input,
textarea,
select {
  width: 100%;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
}
textarea {
  height: 100px;
  resize: vertical;
}
.submit-btn {
  width: 100%;
  padding: 10px;
  border: none;
  border-radius: 4px;
  background: linear-gradient(to right, #4facfe, #00f2fe);
  color: white;
  cursor: pointer;
}
.submit-btn:hover {
  opacity: 0.9;
}
.delete-btn {
  background: linear-gradient(to right, #ef4444, #f87171);
}
</style>

