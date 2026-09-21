<template>
  <article class="song-share-card">
    <img
      class="share-cover"
      :src="coverSrc"
      crossorigin="anonymous"
      alt=""
      @error="handleCoverError"
    />
    <div class="share-cover-mask"></div>
    <div class="orbit orbit-one"></div>
    <div class="orbit orbit-two"></div>

    <div class="share-card-top">
      <div class="share-brand">
        <span class="brand-mark">♪</span>
        <span>SHINING MUSIC</span>
      </div>
      <span class="share-id">S/{{ song.id || '--' }}</span>
    </div>

    <div class="share-card-content">
      <p v-if="lyric" class="share-lyric" :class="lyricSizeClass">{{ lyric }}</p>
      <div v-else class="instrumental-space" aria-hidden="true"></div>

      <div class="share-song-row">
        <div class="share-song-meta">
          <strong>{{ song.title || '未知歌曲' }}</strong>
          <span>{{ artistAndDuration }}</span>
        </div>
        <img v-if="qrCodeUrl" class="share-qr" :src="qrCodeUrl" alt="歌曲二维码" />
      </div>
    </div>
  </article>
</template>

<script>
import defaultCover from '@/assets/default-cover.png';
import { formatShareDuration } from '@/utils/songShare';

export default {
  name: 'SongShareCard',
  props: {
    song: {
      type: Object,
      default: () => ({}),
    },
    lyric: {
      type: String,
      default: '',
    },
    qrCodeUrl: {
      type: String,
      default: '',
    },
  },
  data() {
    return {
      coverFailed: false,
    };
  },
  computed: {
    coverSrc() {
      return this.coverFailed ? defaultCover : (this.song.coverUrl || defaultCover);
    },
    artistAndDuration() {
      const artist = this.song.artistName || '未知歌手';
      const duration = formatShareDuration(this.song.duration);
      return duration ? `${artist} · ${duration}` : artist;
    },
    lyricSizeClass() {
      const length = Array.from(this.lyric || '').length;
      if (length > 42) return 'share-lyric-small';
      if (length > 26) return 'share-lyric-medium';
      return '';
    },
  },
  watch: {
    'song.coverUrl'() {
      this.coverFailed = false;
    },
  },
  methods: {
    handleCoverError() {
      this.coverFailed = true;
    },
  },
};
</script>

<style scoped>
.song-share-card {
  position: relative;
  isolation: isolate;
  width: 360px;
  height: 480px;
  overflow: hidden;
  border-radius: 24px;
  color: #f5f9ff;
  background: #071426;
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.32);
  font-family: Avenir, "PingFang SC", "Microsoft YaHei", sans-serif;
}

.share-cover {
  position: absolute;
  inset: 0;
  z-index: -4;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transform: scale(1.025);
}

.share-cover-mask {
  position: absolute;
  inset: 0;
  z-index: -3;
  background:
    linear-gradient(to bottom, rgba(2, 7, 19, 0.04) 8%, rgba(2, 7, 19, 0.14) 42%, rgba(2, 6, 18, 0.95) 94%),
    radial-gradient(circle at 50% 38%, transparent 0 26%, rgba(5, 7, 20, 0.2) 72%);
}

.orbit {
  position: absolute;
  z-index: -2;
  width: 570px;
  height: 170px;
  left: -104px;
  top: 142px;
  border: 1px solid rgba(189, 235, 255, 0.46);
  border-radius: 50%;
  transform: rotate(-18deg);
  box-shadow: 0 0 24px rgba(102, 215, 255, 0.12);
}

.orbit-two {
  top: 166px;
  border-color: rgba(255, 171, 218, 0.38);
  transform: rotate(17deg);
}

.share-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 22px 22px 0;
}

.share-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
}

.brand-mark {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.52);
  border-radius: 50%;
  background: rgba(5, 10, 25, 0.2);
}

.share-id {
  color: rgba(238, 247, 255, 0.68);
  font-size: 9px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  letter-spacing: 0.12em;
}

.share-card-content {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 30px 24px 23px;
}

.share-lyric {
  min-height: 78px;
  margin: 0 0 25px;
  white-space: pre-line;
  overflow-wrap: anywhere;
  font-family: Georgia, "Songti SC", serif;
  font-size: 28px;
  font-weight: 520;
  line-height: 1.38;
  letter-spacing: 0.01em;
  text-shadow: 0 4px 24px rgba(0, 0, 0, 0.52);
}

.share-lyric-medium { font-size: 24px; }
.share-lyric-small { font-size: 20px; line-height: 1.48; }
.instrumental-space { height: 103px; }

.share-song-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding-top: 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.2);
}

.share-song-meta {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.share-song-meta strong {
  overflow: hidden;
  font-size: 18px;
  font-weight: 680;
  letter-spacing: -0.02em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-song-meta span {
  margin-top: 5px;
  overflow: hidden;
  color: rgba(225, 239, 250, 0.68);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-qr {
  width: 48px;
  height: 48px;
  flex: 0 0 auto;
  border: 4px solid #fff;
  border-radius: 4px;
  background: #fff;
}

</style>
