import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import mitt from 'mitt';
function applyCustomCursors() {
  if (typeof document === 'undefined') return;

  const base = '/cursors';
  const v = Date.now();
  const def = { url: `${base}/cursor-default.png?v=${v}`, hx: 2, hy: 2 };
  const ptr = { url: `${base}/cursor-pointer.png?v=${v}`, hx: 2, hy: 2 };
  const txt = def;
  const grb = def;

  const style = document.getElementById('global-custom-cursor-style') || document.createElement('style');
  style.id = 'global-custom-cursor-style';
  style.innerHTML = `
    html, html *, body, body * {
      cursor: url('${def.url}') ${def.hx} ${def.hy}, auto !important;
    }
    a, a *, button, button *,
    [role="button"], [role="button"] *,
    select, select *, summary, summary *,
    label, label *,
    input[type="submit"], input[type="button"], input[type="reset"],
    input[type="checkbox"], input[type="radio"],
    .clickable, .clickable *,
    .btn, .btn *, .icon-btn, .icon-btn *,
    .nav-item, .nav-item *,
    .mode-btn, .mode-btn *, .mode-menu-item, .mode-menu-item *,
    .logo, .back-btn, .back-btn *,
    .favorite-btn, .favorite-btn *,
    .play-btn, .play-btn *,
    .card, .card *,
    [onclick], [onclick] *,
    .song-cover, .avatar,
    .artist-link {
      cursor: url('${ptr.url}') ${ptr.hx} ${ptr.hy}, pointer !important;
    }
    input:not([type="submit"]):not([type="button"]):not([type="reset"]):not([type="checkbox"]):not([type="radio"]):not([type="range"]),
    textarea,
    [contenteditable="true"] {
      cursor: url('${txt.url}') ${txt.hx} 10, text !important;
    }
    [draggable="true"], .draggable {
      cursor: url('${grb.url}') ${grb.hx} ${grb.hy}, grab !important;
    }
    input[type="range"], input[type="range"]::-webkit-slider-thumb {
      cursor: url('${ptr.url}') ${ptr.hx} ${ptr.hy}, pointer !important;
    }
  `;
  document.head.appendChild(style);
}

applyCustomCursors();

const app = createApp(App);
app.config.devtools = false;
app.config.globalProperties.$bus = mitt();
app.use(router).mount('#app');
