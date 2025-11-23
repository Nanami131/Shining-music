import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import mitt from 'mitt';
import lotusCursor from '@/assets/sward.png';

function applyLotusCursor() {
  if (typeof document === 'undefined') return;
  const size = 64;
  // lotus.png 是 1024x1024，需要先压缩成浏览器可用的尺寸再作为鼠标指针
  const img = new Image();
  img.src = lotusCursor;
  img.onload = () => {
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(img, 0, 0, size, size);
    const dataUrl = canvas.toDataURL('image/png');
    const cursorStyle = document.getElementById('global-lotus-cursor-style') || document.createElement('style');
    cursorStyle.id = 'global-lotus-cursor-style';
    cursorStyle.innerHTML = `
      body, body * {
        cursor: url('${dataUrl}') 16 16, auto !important;
      }
    `;
    document.head.appendChild(cursorStyle);
  };
}

applyLotusCursor();

const app = createApp(App);
app.config.devtools = false;
app.config.globalProperties.$bus = mitt();
app.use(router).mount('#app');
