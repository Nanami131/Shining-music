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

    <div class="section">
      <h3>视频列表</h3>
      <button class="submit-btn refresh-btn" @click="loadVideos" :disabled="videosLoading">
        {{ videosLoading ? '加载中...' : '刷新列表' }}
      </button>
      <div v-if="videos.length === 0 && !videosLoading" class="empty-tip">暂无视频</div>
      <div v-else class="video-list">
        <div v-for="video in videos" :key="video.id" class="video-item">
          <div class="video-item-info">
            <strong>#{{ video.id }}</strong>
            <span>{{ video.title }}</span>
            <span class="video-item-meta">{{ video.singerId ? `歌手ID: ${video.singerId}` : '未绑定歌手' }}</span>
          </div>
          <button class="delete-btn" @click="handleDeleteVideo(video)" :disabled="deletingId === video.id">
            {{ deletingId === video.id ? '删除中...' : '删除' }}
          </button>
        </div>
      </div>
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
      videos: [],
      videosLoading: false,
      deletingId: null,
    };
  },
  created() {
    this.loadVideos();
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
          this.loadVideos();
        } else {
          alert('更新失败：' + (response.data?.message || '未知错误'));
        }
      } catch (error) {
        alert('更新异常：' + error.message);
      }
    },
    async loadVideos() {
      this.videosLoading = true;
      try {
        const res = await musicApi.listVideos();
        if (res.data?.passed) {
          this.videos = res.data.data || [];
        }
      } catch (e) {
        console.warn('加载视频列表失败', e);
      } finally {
        this.videosLoading = false;
      }
    },
    async handleDeleteVideo(video) {
      if (!confirm(`确认删除视频「${video.title}」(ID: ${video.id})？此操作不可撤销。`)) return;
      this.deletingId = video.id;
      try {
        const res = await musicApi.deleteVideo(video.id);
        if (res.data?.passed) {
          alert('删除成功');
          this.videos = this.videos.filter(v => v.id !== video.id);
        } else {
          alert('删除失败：' + (res.data?.message || '未知错误'));
        }
      } catch (e) {
        alert('删除异常：' + e.message);
      } finally {
        this.deletingId = null;
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

.refresh-btn {
  margin-bottom: 15px;
}

.empty-tip {
  text-align: center;
  color: #999;
  padding: 20px 0;
}

.video-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.video-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: #f9fafb;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
}

.video-item-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.video-item-info span {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.video-item-meta {
  color: #9ca3af;
  font-size: 13px;
}

.delete-btn {
  padding: 6px 14px;
  border: none;
  border-radius: 4px;
  background: #ef4444;
  color: white;
  cursor: pointer;
  font-size: 13px;
  flex-shrink: 0;
}

.delete-btn:hover {
  background: #dc2626;
}

.delete-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>

