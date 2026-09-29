import { ref } from 'vue';
import { isAndroidApp } from './androidServer';

// Keep each platform's preference independent; a new Android install starts in cover mode.
const key = isAndroidApp ? 'shining.android.favoriteView' : 'shining.web.favoriteView';

function savedMode() {
  if (typeof window === 'undefined') return 'cover';
  try {
    return window.localStorage.getItem(key) === 'compact' ? 'compact' : 'cover';
  } catch {
    return 'cover';
  }
}

export const webListMode = ref(savedMode());

export function setWebListMode(mode) {
  if (!['cover', 'compact'].includes(mode)) return;
  webListMode.value = mode;
  try { window.localStorage.setItem(key, mode); } catch { /* 仍可在当前页面切换 */ }
}

if (typeof window !== 'undefined') {
  window.addEventListener('storage', event => {
    if (event.key === key || event.key === null) webListMode.value = savedMode();
  });
}
