<template>
  <div class="video-detail-container">
    <div v-if="isLoaded && video" class="video-loaded">
      <h2 class="video-title">{{ video.title || '未知视频' }}</h2>
      <div class="video-panel">
        <video class="video-player" controls :src="video.fileUrl"></video>
      </div>
      <div class="video-meta">
        <div class="meta-row" v-if="singerName">
          <span class="meta-label">歌手</span>
          <span class="meta-value singer-link" @click="goToSinger">{{ singerName }}</span>
        </div>
        <div class="meta-row">
          <span class="meta-label">文件大小</span>
          <span class="meta-value">{{ formatSize(video.sizeBytes) }}</span>
        </div>
        <div class="meta-row" v-if="video.createdAt">
          <span class="meta-label">上传时间</span>
          <span class="meta-value">{{ formatDate(video.createdAt) }}</span>
        </div>
      </div>
    </div>
    <div v-else-if="hasError" class="video-error">
      <h2>视频加载失败</h2>
      <p>请稍后再试。</p>
    </div>
  </div>
</template>

<script>
import musicApi from '@/api/music';

export default {
  name: 'VideoDetail',
  data() {
    return {
      video: null,
      singerName: null,
      isLoaded: false,
      hasError: false,
    };
  },
  created() {
    this.loadVideoInfo();
  },
  watch: {
    '$route.params.id'() {
      this.loadVideoInfo();
    },
  },
  methods: {
    async loadVideoInfo() {
      this.isLoaded = false;
      this.hasError = false;
      try {
        const videoId = this.$route.params.id;
        const response = await musicApi.getVideoInfo(videoId);
        if (response.data?.passed) {
          this.video = response.data.data;
          this.isLoaded = true;
          if (this.video.singerId) {
            this.loadSingerName(this.video.singerId);
          }
        } else {
          this.hasError = true;
          alert('获取视频详情失败：' + (response.data?.message || '未知错误'));
        }
      } catch (error) {
        this.hasError = true;
        alert('获取视频详情异常：' + error.message);
      }
    },
    async loadSingerName(singerId) {
      try {
        const res = await musicApi.getSingerBaseInfo(singerId);
        if (res.data?.passed && res.data.data) {
          this.singerName = res.data.data.name || null;
        }
      } catch (e) {
        this.singerName = null;
      }
    },
    goToSinger() {
      if (this.video && this.video.singerId) {
        this.$router.push(`/singer/${this.video.singerId}`);
      }
    },
    formatSize(sizeBytes) {
      if (!sizeBytes && sizeBytes !== 0) return '未知';
      if (sizeBytes < 1024) return `${sizeBytes} B`;
      if (sizeBytes < 1024 * 1024) return `${(sizeBytes / 1024).toFixed(1)} KB`;
      if (sizeBytes < 1024 * 1024 * 1024) return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`;
      return `${(sizeBytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
    },
    formatDate(dateStr) {
      if (!dateStr) return '';
      const d = new Date(dateStr);
      if (isNaN(d.getTime())) return dateStr;
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    },
  },
};
</script>

<style scoped>
.video-detail-container {
  padding: 32px 20px;
  max-width: 960px;
  margin: 0 auto;
  min-height: 60vh;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
  color: #e2e8f0;
}

.video-title {
  text-align: center;
  margin-bottom: 20px;
  font-size: 24px;
  font-weight: 600;
  color: #f1f5f9;
}

.video-panel {
  background: #000;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
}

.video-player {
  width: 100%;
  display: block;
  max-height: 560px;
}

.video-meta {
  margin-top: 20px;
  background: rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-label {
  font-size: 13px;
  color: #94a3b8;
}

.meta-value {
  font-size: 14px;
  color: #e2e8f0;
  font-weight: 500;
}

.singer-link {
  cursor: pointer;
  color: #7dd3fc;
  transition: color 0.2s;
}

.singer-link:hover {
  color: #38bdf8;
}

.video-error {
  text-align: center;
  padding: 60px 20px;
  color: #94a3b8;
}

.video-error h2 {
  color: #e2e8f0;
  margin-bottom: 8px;
}
</style>
