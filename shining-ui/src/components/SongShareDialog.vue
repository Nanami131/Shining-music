<template>
  <teleport to="body">
    <div v-if="visible" class="share-dialog-mask" @click.self="close">
      <section class="share-dialog" role="dialog" aria-modal="true" aria-label="分享歌曲">
        <header class="share-dialog-header">
          <div>
            <h2>分享歌曲</h2>
            <p>选择一句歌词，生成歌曲卡片</p>
          </div>
          <button class="close-button" type="button" aria-label="关闭" @click="close">×</button>
        </header>

        <div v-if="loading" class="share-state">正在加载歌曲信息...</div>
        <div v-else-if="errorMessage" class="share-state share-error">{{ errorMessage }}</div>
        <div v-else class="share-dialog-body">
          <SongShareCard
            :key="song.id"
            ref="shareCard"
            :song="song"
            :lyric="currentLyric"
            :qr-code-url="qrCodeUrl"
          />

          <div class="share-controls">
            <div v-if="lyricLines.length" class="lyric-switcher">
              <button type="button" :disabled="lyricIndex <= 0" @click="previousLyric">‹</button>
              <span>{{ lyricIndex + 1 }} / {{ lyricLines.length }}</span>
              <button type="button" :disabled="lyricIndex >= lyricLines.length - 1" @click="nextLyric">›</button>
            </div>
            <p v-else class="no-lyrics">这首歌暂无歌词，卡片将突出展示封面和歌曲信息。</p>

            <div class="share-actions">
              <button type="button" :disabled="exporting" @click="saveImage">保存图片</button>
              <button type="button" :disabled="exporting" @click="copyLink">复制链接</button>
              <button class="primary" type="button" :disabled="exporting" @click="shareCard">
                {{ exporting ? '生成中...' : '分享' }}
              </button>
            </div>
            <p class="share-notice" aria-live="polite">{{ notice }}</p>
          </div>
        </div>
      </section>
    </div>
  </teleport>
</template>

<script>
import musicApi from '@/api/music';
import SongShareCard from '@/components/SongShareCard.vue';
import { registerPlugin } from '@capacitor/core';
import { isAndroidApp } from '@/utils/androidServer';
import {
  buildShareFileName,
  buildSongDetailUrl,
  extractShareLyrics,
  findShareLyricIndex,
} from '@/utils/songShare';

export default {
  name: 'SongShareDialog',
  components: { SongShareCard },
  data() {
    return {
      visible: false,
      loading: false,
      exporting: false,
      errorMessage: '',
      notice: '',
      song: {},
      lyricLines: [],
      lyricIndex: 0,
      shareUrl: '',
      qrCodeUrl: '',
      requestSequence: 0,
      noticeTimer: null,
    };
  },
  computed: {
    currentLyric() {
      return this.lyricLines[this.lyricIndex]?.text || '';
    },
  },
  mounted() {
    this.$bus.on('openSongShare', this.open);
    window.addEventListener('keydown', this.handleKeydown);
  },
  beforeUnmount() {
    this.$bus.off('openSongShare', this.open);
    window.removeEventListener('keydown', this.handleKeydown);
    if (this.noticeTimer) clearTimeout(this.noticeTimer);
  },
  methods: {
    async nativeImagePayload(blob) {
      const dataUrl = await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = () => reject(new Error('图片读取失败'));
        reader.readAsDataURL(blob);
      });
      const base64 = String(dataUrl).split(',')[1];
      if (!base64) throw new Error('图片数据为空');
      return {
        base64,
        fileName: buildShareFileName(this.song.title),
      };
    },
    async open(request) {
      const payload = request && typeof request === 'object' ? request : { songId: request };
      const songId = Number(payload.songId);
      if (!Number.isInteger(songId) || songId <= 0) return;

      const sequence = ++this.requestSequence;
      this.visible = true;
      this.loading = true;
      this.exporting = false;
      this.errorMessage = '';
      this.notice = '';
      this.song = {};
      this.lyricLines = [];
      this.lyricIndex = 0;
      this.shareUrl = buildSongDetailUrl(songId);
      this.qrCodeUrl = '';

      try {
        const response = await musicApi.getSongShareInfo(songId);
        if (sequence !== this.requestSequence || !this.visible) return;
        if (!response.data?.passed || !response.data.data) {
          this.errorMessage = response.data?.message || '歌曲分享信息加载失败';
          return;
        }

        this.song = response.data.data;
        this.lyricLines = extractShareLyrics(this.song.lyrics, payload.preferredLanguage);
        this.lyricIndex = findShareLyricIndex(this.lyricLines, payload.currentTime);
        this.generateQrCode(sequence);
      } catch (error) {
        if (sequence === this.requestSequence && this.visible) {
          this.errorMessage = error.message || '歌曲分享信息加载失败';
        }
      } finally {
        if (sequence === this.requestSequence) {
          this.loading = false;
        }
      }
    },
    close() {
      this.requestSequence += 1;
      this.visible = false;
      this.exporting = false;
    },
    handleKeydown(event) {
      if (this.visible && event.key === 'Escape') this.close();
    },
    previousLyric() {
      if (this.lyricIndex > 0) this.lyricIndex -= 1;
    },
    nextLyric() {
      if (this.lyricIndex < this.lyricLines.length - 1) this.lyricIndex += 1;
    },
    async generateQrCode(sequence) {
      try {
        const qrModule = await import('qrcode');
        if (sequence !== this.requestSequence || !this.visible) return;
        const qrCode = qrModule.default || qrModule;
        const dataUrl = await qrCode.toDataURL(this.shareUrl, {
          errorCorrectionLevel: 'M',
          margin: 1,
          width: 180,
          color: { dark: '#06101fff', light: '#ffffffff' },
        });
        if (sequence === this.requestSequence && this.visible) {
          this.qrCodeUrl = dataUrl;
        }
      } catch (error) {
        this.qrCodeUrl = '';
      }
    },
    async waitForCardAssets(node) {
      const images = Array.from(node.querySelectorAll('img'));
      const imageReady = Promise.all(images.map(image => {
        if (image.complete) return Promise.resolve();
        return new Promise(resolve => {
          image.addEventListener('load', resolve, { once: true });
          image.addEventListener('error', resolve, { once: true });
        });
      }));
      await Promise.race([
        imageReady,
        new Promise(resolve => setTimeout(resolve, 5000)),
      ]);
      if (document.fonts?.ready) await document.fonts.ready;
    },
    async createImageBlob() {
      await this.$nextTick();
      const node = this.$refs.shareCard?.$el;
      if (!node) throw new Error('卡片尚未生成');
      await this.waitForCardAssets(node);
      const imageModule = await import('html-to-image');
      const blob = await imageModule.toBlob(node, {
        pixelRatio: 3,
        cacheBust: true,
        backgroundColor: '#071426',
      });
      if (!blob) throw new Error('图片生成失败');
      return blob;
    },
    downloadBlob(blob) {
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = buildShareFileName(this.song.title);
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(objectUrl), 30000);
    },
    async saveImage() {
      if (this.exporting) return;
      this.exporting = true;
      try {
        const blob = await this.createImageBlob();
        if (isAndroidApp) {
          const payload = await this.nativeImagePayload(blob);
          await registerPlugin('SongShare').saveImage(payload);
        } else {
          this.downloadBlob(blob);
        }
        this.showNotice('图片已保存');
      } catch (error) {
        this.showNotice(error.message || '图片保存失败');
      } finally {
        this.exporting = false;
      }
    },
    async copyText(text) {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
        return;
      }
      const input = document.createElement('textarea');
      input.value = text;
      input.setAttribute('readonly', '');
      input.style.position = 'fixed';
      input.style.opacity = '0';
      document.body.appendChild(input);
      input.select();
      const copied = document.execCommand('copy');
      input.remove();
      if (!copied) throw new Error('复制失败');
    },
    async copyLink() {
      try {
        if (isAndroidApp) {
          await registerPlugin('SongShare').copyText({ text: this.shareUrl });
        } else {
          await this.copyText(this.shareUrl);
        }
        this.showNotice('歌曲链接已复制');
      } catch (error) {
        this.showNotice(error.message || '链接复制失败');
      }
    },
    async shareCard() {
      if (this.exporting) return;
      this.exporting = true;
      try {
        const blob = await this.createImageBlob();
        if (isAndroidApp) {
          const payload = await this.nativeImagePayload(blob);
          await registerPlugin('SongShare').shareImage({
            ...payload,
            text: `${this.song.title || '歌曲'} - ${this.song.artistName || '未知歌手'}\n${this.shareUrl}`,
          });
          this.showNotice('分享面板已关闭');
          return;
        }
        const file = new File([blob], buildShareFileName(this.song.title), { type: 'image/png' });
        const shareData = {
          title: this.song.title || '分享歌曲',
          text: `${this.song.title || '歌曲'} - ${this.song.artistName || '未知歌手'}`,
          url: this.shareUrl,
          files: [file],
        };

        if (navigator.share && navigator.canShare?.({ files: [file] })) {
          try {
            await navigator.share(shareData);
            this.showNotice('分享完成');
            return;
          } catch (error) {
            if (error.name === 'AbortError') return;
          }
        }

        this.downloadBlob(blob);
        await this.copyText(this.shareUrl);
        this.showNotice('图片已保存，歌曲链接已复制');
      } catch (error) {
        this.showNotice(error.message || '分享失败');
      } finally {
        this.exporting = false;
      }
    },
    showNotice(message) {
      this.notice = message;
      if (this.noticeTimer) clearTimeout(this.noticeTimer);
      this.noticeTimer = setTimeout(() => {
        this.notice = '';
      }, 2200);
    },
  },
};
</script>

<style scoped>
.share-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 12000;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(2, 6, 23, 0.72);
  backdrop-filter: blur(12px);
}

.share-dialog {
  width: min(820px, 100%);
  max-height: calc(100vh - 40px);
  overflow: auto;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 26px;
  color: #edf7ff;
  background:
    radial-gradient(circle at 5% 8%, rgba(56, 189, 248, 0.2), transparent 35%),
    radial-gradient(circle at 95% 95%, rgba(168, 85, 247, 0.18), transparent 38%),
    #07101f;
  box-shadow: 0 30px 90px rgba(0, 0, 0, 0.46);
}

.share-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22px 24px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.share-dialog-header h2 { margin: 0; font-size: 20px; }
.share-dialog-header p { margin: 5px 0 0; color: #8ea3b8; font-size: 12px; }

.close-button {
  width: 34px;
  height: 34px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 50%;
  color: #dcecf8;
  background: rgba(255, 255, 255, 0.06);
  font-size: 21px;
  cursor: pointer;
}

.share-state {
  padding: 80px 24px;
  color: #9fb3c6;
  text-align: center;
}

.share-error { color: #fda4af; }

.share-dialog-body {
  display: grid;
  grid-template-columns: 360px minmax(220px, 1fr);
  gap: 28px;
  align-items: center;
  padding: 24px;
}

.share-controls { min-width: 0; }

.lyric-switcher {
  display: grid;
  grid-template-columns: 42px 1fr 42px;
  align-items: center;
  margin-bottom: 22px;
  padding: 10px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.05);
}

.lyric-switcher span { color: #a9bacb; text-align: center; font-size: 12px; }
.lyric-switcher button {
  height: 36px;
  border: 0;
  border-radius: 12px;
  color: #dff6ff;
  background: rgba(114, 220, 255, 0.12);
  font-size: 24px;
  cursor: pointer;
}
.lyric-switcher button:disabled { opacity: 0.28; cursor: not-allowed; }
.no-lyrics { margin: 0 0 22px; color: #9fb3c6; font-size: 13px; line-height: 1.7; }

.share-actions { display: grid; gap: 10px; }
.share-actions button {
  min-height: 42px;
  border: 1px solid rgba(255, 255, 255, 0.13);
  border-radius: 13px;
  color: #dcecf8;
  background: rgba(255, 255, 255, 0.07);
  cursor: pointer;
}
.share-actions button.primary {
  border: 0;
  color: #06101f;
  font-weight: 700;
  background: linear-gradient(135deg, #72dcff, #a99aff);
}
.share-actions button:disabled { opacity: 0.56; cursor: wait; }
.share-notice { min-height: 20px; margin: 12px 0 0; color: #7ee7c4; text-align: center; font-size: 12px; }

@media (max-width: 700px) {
  .share-dialog-mask { padding: 8px; }
  .share-dialog { max-height: calc(100vh - 16px); border-radius: 20px; }
  .share-dialog-body { grid-template-columns: 1fr; justify-items: center; padding: 16px; }
  .share-controls { width: min(360px, 100%); }
  .song-share-card { transform-origin: top center; }
}

@media (max-width: 390px) {
  .share-dialog-body :deep(.song-share-card) {
    width: 320px;
    height: 426px;
  }
}
</style>
