<template>
  <div class="video-detail-container">
    <div v-if="isLoaded && video">
      <h2>{{ video.title || '未知视频' }}</h2>
      <div class="video-panel">
        <video class="video-player" controls :src="video.fileUrl"></video>
      </div>
      <div class="video-info">
        <p><strong>视频 ID：</strong>{{ video.id }}</p>
        <p><strong>歌手 ID：</strong>{{ video.singerId ?? '未绑定' }}</p>
        <p><strong>文件大小：</strong>{{ formatSize(video.sizeBytes) }}</p>
        <p><strong>创建时间：</strong>{{ video.createdAt || '未知' }}</p>
      </div>
    </div>
    <div v-else-if="hasError">
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
      isLoaded: false,
      hasError: false,
    };
  },
  created() {
    this.loadVideoInfo();
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
        } else {
          this.hasError = true;
          alert('获取视频详情失败：' + (response.data?.message || '未知错误'));
        }
      } catch (error) {
        this.hasError = true;
        alert('获取视频详情异常：' + error.message);
      }
    },
    formatSize(sizeBytes) {
      if (!sizeBytes && sizeBytes !== 0) return '未知';
      if (sizeBytes < 1024) return `${sizeBytes} B`;
      if (sizeBytes < 1024 * 1024) return `${(sizeBytes / 1024).toFixed(2)} KB`;
      if (sizeBytes < 1024 * 1024 * 1024) return `${(sizeBytes / (1024 * 1024)).toFixed(2)} MB`;
      return `${(sizeBytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
    },
  },
};
</script>

<style scoped>
.video-detail-container {
  padding: 20px;
  max-width: 960px;
  margin: 0 auto;
  background: linear-gradient(to bottom, #e0f7fa, #ffffff);
}

h2 {
  text-align: center;
  margin-bottom: 16px;
}

.video-panel {
  background: #000;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
}

.video-player {
  width: 100%;
  display: block;
  max-height: 560px;
}

.video-info {
  margin-top: 16px;
  background: #fff;
  border-radius: 10px;
  padding: 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.video-info p {
  margin: 8px 0;
}
</style>

