<template>
  <div class="dev-container">
    <h2>视频管理（开发者模式）</h2>

    <div class="section">
      <h3>上传视频（单点上传）</h3>
      <form @submit.prevent="handleUploadVideo">
        <div class="form-item">
          <label>标题</label>
          <input v-model="uploadForm.title" placeholder="请输入视频标题" required />
        </div>
        <div class="form-item">
          <label>歌手 ID（可选）</label>
          <input v-model="uploadForm.singerId" type="number" placeholder="可留空" />
        </div>
        <div class="form-item">
          <label>视频文件</label>
          <input type="file" accept="video/*" @change="handleVideoChange" required />
        </div>
        <button type="submit" class="submit-btn">上传视频</button>
      </form>
    </div>

    <div class="section">
      <h3>编辑视频元信息</h3>
      <form @submit.prevent="handleUpdateMeta">
        <div class="form-item">
          <label>视频 ID</label>
          <input v-model="editForm.id" type="number" placeholder="请输入视频 ID" required />
        </div>
        <div class="form-item">
          <label>标题</label>
          <input v-model="editForm.title" placeholder="请输入新标题" />
        </div>
        <div class="form-item">
          <label>歌手 ID（可选）</label>
          <input v-model="editForm.singerId" type="number" placeholder="可留空" />
        </div>
        <div class="form-item">
          <label>封面 URL（可选）</label>
          <input v-model="editForm.coverUrl" placeholder="请输入封面URL" />
        </div>
        <button type="submit" class="submit-btn">保存元信息</button>
      </form>
    </div>
  </div>
</template>

<script>
import md5 from 'js-md5';
import musicApi from '@/api/music';

export default {
  name: 'VideoManage',
  data() {
    return {
      uploadForm: {
        title: '',
        singerId: null,
        file: null,
        md5: '',
      },
      editForm: {
        id: null,
        title: '',
        singerId: null,
        coverUrl: '',
      },
    };
  },
  methods: {
    handleVideoChange(event) {
      const file = event.target.files[0];
      if (file) {
        this.uploadForm.file = file;
        this._videoMd5Promise = new Promise((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => {
            this.uploadForm.md5 = md5(reader.result);
            resolve();
          };
          reader.onerror = () => reject(new Error('文件读取失败'));
          reader.readAsArrayBuffer(file);
        });
      }
    },
    async handleUploadVideo() {
      try {
        if (this._videoMd5Promise) await this._videoMd5Promise;
        const response = await musicApi.uploadVideo(
          this.uploadForm.singerId,
          this.uploadForm.title,
          this.uploadForm.file,
          this.uploadForm.md5
        );
        if (response.data?.passed) {
          alert('上传视频成功');
          this.uploadForm = {
            title: '',
            singerId: null,
            file: null,
            md5: '',
          };
        } else {
          alert('上传视频失败：' + (response.data?.message || '未知错误'));
        }
      } catch (error) {
        alert('上传视频异常：' + error.message);
      }
    },
    async handleUpdateMeta() {
      try {
        const payload = {
          id: this.editForm.id,
          title: this.editForm.title || null,
          singerId: this.editForm.singerId === '' ? null : this.editForm.singerId,
          coverUrl: this.editForm.coverUrl || null,
        };
        const response = await musicApi.updateVideoMeta(payload);
        if (response.data?.passed) {
          alert('更新成功');
        } else {
          alert('更新失败：' + (response.data?.message || '未知错误'));
        }
      } catch (error) {
        alert('更新异常：' + error.message);
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

