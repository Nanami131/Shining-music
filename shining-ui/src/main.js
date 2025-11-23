import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import mitt from 'mitt';
import customCursorAsset from '@/assets/rose.png';

function applyCustomCursor() {
  if (typeof document === 'undefined') return;
  const size = 32; // 缩小到接近系统指针的尺寸，操作更精确
  const hotSpot = { x: 6, y: 6 }; // 将热点放到激光剑尖端附近
  const img = new Image();
  img.src = customCursorAsset;
  img.onload = () => {
    const canvas = document.createElement('canvas');
    canvas.width = size*0.8;
    canvas.height = size*1.5;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(img, 0, 0, size, size);
    const dataUrl = canvas.toDataURL('image/png');
    const cursorStyle = document.getElementById('global-custom-cursor-style') || document.createElement('style');
    cursorStyle.id = 'global-custom-cursor-style';
    cursorStyle.innerHTML = `
      body, body * {
        cursor: url('${dataUrl}') ${hotSpot.x} ${hotSpot.y}, auto !important;
      }
    `;
    document.head.appendChild(cursorStyle);
  };
}

applyCustomCursor();

const app = createApp(App);
app.config.devtools = false;
app.config.globalProperties.$bus = mitt();
app.use(router).mount('#app');
