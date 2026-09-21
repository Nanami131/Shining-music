import { normalizeLyricLang, parseLyrics } from '@/utils/lrcParser';
import { getAndroidServer, isAndroidApp } from '@/utils/androidServer';

function getLineText(line, preferredLanguage) {
    if (!line || line.break) return '';
    if (line.text) return String(line.text).trim();

    const preferred = normalizeLyricLang(preferredLanguage);
    if (preferred && line[preferred]) {
        return String(line[preferred]).trim();
    }

    for (const [key, value] of Object.entries(line)) {
        if (key !== 'time' && key !== 'break' && value) {
            return String(value).trim();
        }
    }
    return '';
}

export function extractShareLyrics(lyrics, preferredLanguage) {
    if (!Array.isArray(lyrics) || !lyrics.length) return [];

    const normalizedPreferred = normalizeLyricLang(preferredLanguage);
    const selected = lyrics.find(
        item => normalizeLyricLang(item && item.languageMsg) === normalizedPreferred
    ) || lyrics[0];
    if (!selected || !selected.content) return [];

    return parseLyrics(selected.content)
        .map(line => ({
            time: Number.isFinite(line.time) ? line.time : null,
            text: getLineText(line, normalizedPreferred),
        }))
        .filter(line => line.text);
}

export function findShareLyricIndex(lines, currentTime) {
    if (!Array.isArray(lines) || !lines.length) return 0;
    const time = Number(currentTime);
    if (!Number.isFinite(time) || time < 0) return 0;

    let matchedIndex = 0;
    for (let index = 0; index < lines.length; index += 1) {
        const lineTime = lines[index].time;
        if (lineTime === null || lineTime > time) break;
        matchedIndex = index;
    }
    return matchedIndex;
}

export function formatShareDuration(seconds) {
    const duration = Number(seconds);
    if (!Number.isFinite(duration) || duration <= 0) return '';
    const minutes = Math.floor(duration / 60);
    const remain = Math.floor(duration % 60);
    return `${minutes}:${String(remain).padStart(2, '0')}`;
}

export function buildSongDetailUrl(songId) {
    if (typeof window === 'undefined') return `/song/${songId}`;
    const origin = isAndroidApp ? getAndroidServer() : window.location.origin;
    return `${origin}/song/${songId}`;
}

export function buildShareFileName(songTitle) {
    const safeTitle = String(songTitle || '歌曲')
        .replace(/[\\/:*?"<>|]/g, '-')
        .trim();
    return `${safeTitle || '歌曲'}-Shining-Music.png`;
}
