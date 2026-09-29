import { isAndroidApp } from '@/utils/androidServer';
import { getCachedSong, listCachedSongs } from './audioCacheService';

export function localAccountId() {
  try {
    const id = JSON.parse(localStorage.getItem('userBase') || '{}').id;
    return id == null ? null : String(id);
  } catch { return null; }
}

export function isOfflineSelected() {
  if (!isAndroidApp) return false;
  const id = localAccountId();
  if (!id) return false;
  try {
    return JSON.parse(localStorage.getItem(`shining.offline.state.${id}`) || '{}').selectedMode === 'offline';
  } catch { return false; }
}

export function localQueue(accountId) {
  if (accountId == null || String(accountId) !== localAccountId()) return [];
  try {
    const stored = JSON.parse(localStorage.getItem(`shining.offline.state.${accountId}`) || '{}');
    return Array.isArray(stored.queue) ? [...new Set(stored.queue.map(String).filter(id => /^[0-9]{1,20}$/.test(id)))] : [];
  } catch { return []; }
}

// Only fully verified files belonging to the current account can appear in offline views.
export async function localSongs(accountId) {
  if (!isAndroidApp || accountId == null || String(accountId) !== localAccountId()) return [];
  const songs = await listCachedSongs(accountId);
  const resolved = await Promise.all(songs.map(async song => {
    const cached = await getCachedSong(song.id, accountId);
    if (cached.status !== 'READY') return null;
    return { ...song, coverUrl: cached.coverUri || null, artistName: cached.metadata?.artistName || song.artistName };
  }));
  return resolved.filter(Boolean);
}

export function localPlaylist(accountId) {
  return {
    id: 'local',
    name: '当前播放列表',
    description: '',
    userId: accountId,
    coverUrl: null,
  };
}
