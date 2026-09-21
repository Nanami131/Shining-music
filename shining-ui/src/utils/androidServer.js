import { Capacitor } from '@capacitor/core';

export const isAndroidApp = Capacitor.getPlatform() === 'android';

// 当前已部署的前端入口同时代理 /api/ 与 /minio/。
export const defaultAndroidServer = 'http://10.24.97.9:5173';
const storageKey = 'shining.android.serverOrigin';

export function normalizeServerOrigin(value) {
  try {
    const url = new URL(value.trim());
    if (!['https:', 'http:'].includes(url.protocol) || !url.hostname ||
        url.username || url.password || url.search || url.hash || url.pathname !== '/') {
      return null;
    }
    return url.origin;
  } catch {
    return null;
  }
}

export function getAndroidServer() {
  if (!isAndroidApp) return '';
  return normalizeServerOrigin(localStorage.getItem(storageKey) || '') || defaultAndroidServer;
}

export function setAndroidServer(value) {
  const origin = normalizeServerOrigin(value);
  if (!origin) return false;
  localStorage.setItem(storageKey, origin);
  return true;
}
