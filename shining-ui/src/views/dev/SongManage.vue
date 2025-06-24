<template>
  <div class="dev-container">
    <h2>歌曲管理（开发者模式）</h2>

    <!-- 上传歌曲 -->
    <div class="section">
      <h3>上传歌曲</h3>
      <form @submit.prevent="handleUploadSong">
        <div class="form-item">
          <label>歌手 ID</label>
          <input v-model="songForm.id" type="number" placeholder="请输入歌手 ID" required />
        </div>
        <div class="form-item">
          <label>歌曲文件</label>
          <input type="file" accept="audio/*" @change="handleSongChange" required />
        </div>
        <button type="submit" class="submit-btn">上传歌曲</button>
      </form>
    </div>
  </div>
</template>

<script>
import musicApi from '@/api/music';
import md5 from 'js-md5';

export default {
  name: 'SongManage',
  data() {
    return {
      songForm: {
        id: null,
        file: null,
        md5: '',
      },
    };
  },
  methods: {
    handleSongChange(event) {
      const file = event.target.files[0];
      if (file) {
        this.songForm.file = file;
        const reader = new FileReader();
        reader.onload = () => {
          this.songForm.md5 = md5(reader.result);
        };
        reader.readAsArrayBuffer(file);
      }
    },
    async handleUploadSong() {
      try {
        const response = await musicApi.uploadSong(
            this.songForm.id,
            this.songForm.file,
            this.songForm.md5
        );
        if (response.data.passed) {
          alert('上传歌曲成功');
          this.songForm = { id: null, file: null, md5: '' };
        } else {
          alert('上传歌曲失败：' + response.data.message);
        }
      } catch (error) {
        alert('上传歌曲出错：' + error.message);
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
input {
  width: 100%;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
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
</style>