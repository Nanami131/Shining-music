import { Capacitor, registerPlugin } from '@capacitor/core';
import musicApi from '@/api/music';
import { getAndroidServer, isAndroidApp } from '@/utils/androidServer';

const nativeCache = isAndroidApp ? registerPlugin('OfflineAudio') : null;
const started = new Set();

export async function setNativeOfflineMode(offline) {
  if (nativeCache) await nativeCache.setOfflineMode({ offline });
}

function accountKey(userId) {
  return userId == null ? '0' : String(userId);
}

async function completeSongInfo(details, isStillOnline) {
  if (!details?.id || !details?.title || !details?.fileUrl) {
    throw new Error('歌曲音源或基本信息不完整');
  }
  if (!Number.isFinite(Number(details.duration)) || Number(details.duration) <= 0) {
    throw new Error('未能取得歌曲完整时长');
  }

  let artistName = '未知歌手';
  if (details.artistId != null) {
    if (!isStillOnline()) return null;
    const response = await musicApi.getSingerBaseInfo(details.artistId);
    const singer = response?.data?.passed ? response.data.data : null;
    if (!singer?.name) throw new Error('歌手信息尚未取得');
    artistName = singer.name;
  }

  // A null DTO field does not distinguish missing lyrics from a failed lookup.
  // Check the dedicated endpoint before recording an explicit source absence.
  let sourceLyrics = details.allLyrics;
  if (sourceLyrics == null) {
    if (!isStillOnline()) return null;
    const response = await musicApi.getAllLyrics(details.id);
    if (response?.data?.passed && Array.isArray(response.data.data)) {
      sourceLyrics = response.data.data;
    } else if (response?.data?.message === '歌词不存在') {
      sourceLyrics = [];
    } else {
      throw new Error('歌词来源未能确认');
    }
  }
  if (!Array.isArray(sourceLyrics)) {
    throw new Error('歌词信息不完整');
  }
  const lyrics = sourceLyrics.map(lyric => {
    if (typeof lyric.content !== 'string' || !lyric.languageMsg) {
      throw new Error('歌词正文或语言未取得');
    }
    return {
      id: lyric.id,
      languageMsg: lyric.languageMsg,
      content: lyric.content,
    };
  });

  const { fileUrl, coverUrl, allLyrics, playSessionId, ...song } = details;
  return {
    metadata: { ...song, artistName },
    lyrics,
    audioUrl: fileUrl,
    coverUrl: coverUrl || null,
  };
}

/** Called only after a real online play starts. No web-side cache is created. */
export async function cachePlayedSong(details, userId, isStillOnline = () => true) {
  if (!nativeCache || !details?.id) return;
  const accountId = accountKey(userId);
  const songId = String(details.id);
  const key = `${accountId}:${songId}`;
  if (started.has(key)) return;
  started.add(key);
  try {
    const cached = await nativeCache.getCachedSong({ accountId, songId });
    if (!isStillOnline()) return;
    if (cached.status === 'READY' || cached.status === 'QUEUED' ||
        cached.status === 'DOWNLOADING' || cached.status === 'VERIFYING') return;
    const bundle = await completeSongInfo(details, isStillOnline);
    if (!isStillOnline() || !bundle) return;
    await nativeCache.enqueueDownload({
      accountId,
      songId,
      serverOrigin: getAndroidServer(),
      ...bundle,
    });
  } finally {
    started.delete(key);
  }
}

/** Returns a complete local snapshot without fetching any backend URL. */
export async function getCachedSong(songId, userId) {
  if (!nativeCache) return { status: 'NOT_SUPPORTED' };
  const result = await nativeCache.getCachedSong({
    accountId: accountKey(userId),
    songId: String(songId),
  });
  if (result.status !== 'READY') return result;
  return {
    ...result,
    audioUri: Capacitor.convertFileSrc(result.audioFileUri),
    coverUri: result.coverFileUri ? Capacitor.convertFileSrc(result.coverFileUri) : null,
  };
}

export async function listCachedSongs(userId) {
  if (!nativeCache) return [];
  const response = await nativeCache.listCachedSongs({ accountId: accountKey(userId) });
  return response.songs || [];
}
