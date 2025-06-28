<template>
  <div class="dev-container">
    <h2>歌曲管理（开发者模式）</h2>

    <!-- 创建歌曲信息 -->
    <div class="section">
      <h3>创建歌曲信息</h3>
      <form @submit.prevent="handleCreateSong">
        <div class="form-item">
          <label>歌曲标题</label>
          <input v-model="songForm.title" placeholder="请输入歌曲标题" required />
        </div>
        <div class="form-item">
          <label>歌手 ID</label>
          <input v-model="songForm.artistId" type="number" placeholder="请输入歌手 ID" required />
        </div>
        <div class="form-item">
          <label>专辑 ID（可选）</label>
          <input v-model="songForm.albumId" type="number" placeholder="请输入专辑 ID" />
        </div>
        <div class="form-item">
          <label>状态（可选）</label>
          <input v-model="songForm.status" type="number" placeholder="请输入状态值" />
        </div>
        <button type="submit" class="submit-btn">创建歌曲</button>
      </form>
    </div>

    <!-- 上传歌曲文件 -->
    <div class="section">
      <h3>上传歌曲文件</h3>
      <form @submit.prevent="handleUploadSong">
        <div class="form-item">
          <label>歌曲 ID</label>
          <input v-model="songFileForm.id" type="number" placeholder="请输入歌曲 ID" required />
        </div>
        <div class="form-item">
          <label>歌曲文件</label>
          <input type="file" accept="audio/*" @change="handleSongChange" required />
        </div>
        <button type="submit" class="submit-btn">上传歌曲</button>
      </form>
    </div>

    <!-- 上传歌词 -->
    <div class="section">
      <h3>上传歌词</h3>
      <form @submit.prevent="handleUploadLyrics">
        <div class="form-item">
          <label>歌曲 ID</label>
          <input v-model="lyricsForm.songId" type="number" placeholder="请输入歌曲 ID" required />
        </div>
        <div class="form-item">
          <label>语言描述</label>
          <input v-model="lyricsForm.languageMsg" placeholder="请输入语言描述" required />
        </div>
        <div class="form-item">
          <label>歌词文件</label>
          <input type="file" accept=".txt" @change="handleLyricsChange" required />
        </div>
        <button type="submit" class="submit-btn">上传歌词</button>
      </form>
    </div>

    <!-- 上传歌曲封面 -->
    <div class="section">
      <h3>上传歌曲封面</h3>
      <form @submit.prevent="handleUploadSongCover">
        <div class="form-item">
          <label>歌曲 ID</label>
          <input v-model="coverForm.id" type="number" placeholder="请输入歌曲 ID" required />
        </div>
        <div class="form-item">
          <label>封面文件</label>
          <input type="file" accept="image/*" @change="handleCoverChange" required />
        </div>
        <button type="submit" class="submit-btn">上传封面</button>
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
        title: '',
        artistId: null,
        albumId: null,
        status: null,
      },
      songFileForm: {
        id: null,
        file: null,
        md5: '',
      },
      lyricsForm: {
        songId: null,
        languageMsg: '',
        file: null,
      },
      coverForm: {
        id: null,
        file: null,
        md5: '',
      },
    };
  },
  methods: {
    async handleCreateSong() {
      try {
        const response = await musicApi.createSong(this.songForm);
        if (response.data.passed) {
          alert('创建歌曲成功');
          this.songForm = { title: '', artistId: null, albumId: null, status: null };
        } else {
          alert('创建歌曲失败：' + response.data.message);
        }
      } catch (error) {
        alert('创建歌曲出错：' + error.message);
      }
    },
    handleSongChange(event) {
      const file = event.target.files[0];
      if (file) {
        this.songFileForm.file = file;
        const reader = new FileReader();
        reader.onload = () => {
          this.songFileForm.md5 = md5(reader.result);
        };
        reader.readAsArrayBuffer(file);
      }
    },
    async handleUploadSong() {
      try {
        const response = await musicApi.uploadSong(
            this.songFileForm.id,
            this.songFileForm.file,
            this.songFileForm.md5
        );
        if (response.data.passed) {
          alert('上传歌曲成功');
          this.songFileForm = { id: null, file: null, md5: '' };
        } else {
          alert('上传歌曲失败：' + response.data.message);
        }
      } catch (error) {
        alert('上传歌曲出错：' + error.message);
      }
    },
    handleLyricsChange(event) {
      const file = event.target.files[0];
      if (file) {
        this.lyricsForm.file = file;
      }
    },
    async handleUploadLyrics() {
      try {
        const response = await musicApi.uploadLyrics(
            this.lyricsForm.songId,
            this.lyricsForm.file,
            this.lyricsForm.languageMsg
        );
        if (response.data.passed) {
          alert('上传歌词成功');
          this.lyricsForm = { songId: null, languageMsg: '', file: null };
        } else {
          alert('上传歌词失败：' + response.data.message);
        }
      } catch (error) {
        alert('上传歌词出错：' + error.message);
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
    async handleUploadSongCover() {
      try {
        const response = await musicApi.uploadSongCover(
            this.coverForm.id,
            this.coverForm.file,
            this.coverForm.md5
        );
        if (response.data.passed) {
          alert('上传封面成功');
          this.coverForm = { id: null, file: null, md5: '' };
        } else {
          alert('上传封面失败：' + response.data.message);
        }
      } catch (error) {
        alert('上传封面出错：' + error.message);
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
select {
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