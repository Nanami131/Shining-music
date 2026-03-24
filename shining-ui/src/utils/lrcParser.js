/**
 * Shared LRC parser for SongDetail and BottomBar.
 * Supports: [mm:ss.xx], [mm:ss], [mm:ss.xxx], extended [time][lang]text
 */

const TIME_RE = /^\[(\d+:\d+(?:\.\d+)?)\]/;
const EXTENDED_RE = /^\[(\d+:\d+(?:\.\d+)?)\]\[(\w+)\](.+)$/;
const STANDARD_RE = /^\[(\d+:\d+(?:\.\d+)?)\](.*)$/;
const META_RE = /^(作词|作曲|编曲|ar|ti|al|by|offset)\s*[:：]/i;

export function timeToSeconds(timeStr) {
  const [min, sec] = timeStr.split(':').map(parseFloat);
  return min * 60 + sec;
}

export function parseLyrics(content) {
  if (!content) return [];

  const lines = content.split('\n').map(l => l.trim());
  const timeMap = {};

  lines.forEach(line => {
    if (!line || !TIME_RE.test(line)) return;

    const langMatch = line.match(EXTENDED_RE);
    if (langMatch) {
      const [, time, lang, text] = langMatch;
      const trimmed = text.trim();
      if (META_RE.test(trimmed)) return;
      const t = timeToSeconds(time);
      if (!timeMap[t]) timeMap[t] = { time: t };
      timeMap[t][lang] = trimmed;
      return;
    }

    const stdMatch = line.match(STANDARD_RE);
    if (stdMatch) {
      const [, time, text] = stdMatch;
      const trimmed = (text || '').trim();
      if (META_RE.test(trimmed)) return;
      const t = timeToSeconds(time);
      if (!timeMap[t]) timeMap[t] = { time: t };
      if (trimmed) {
        timeMap[t].text = trimmed;
      } else if (!timeMap[t].text && !timeMap[t].ja && !timeMap[t].zh && !timeMap[t].en) {
        timeMap[t].break = true;
      }
    }
  });

  return Object.values(timeMap).sort((a, b) => a.time - b.time);
}

/**
 * Merge multiple parsed lyric arrays into multilingual entries.
 * @param {Array<{lang: string, lines: Array}>} sources - [{lang:'ja', lines: [...]}, {lang:'zh', lines: [...]}]
 * @returns {Array<{time: number, langs: {[lang]: string}}>}
 */
export function mergeMultiLang(sources) {
  const timeMap = {};
  sources.forEach(({ lang, lines }) => {
    lines.forEach(l => {
      if (l.break) {
        if (!timeMap[l.time]) timeMap[l.time] = { time: l.time, break: true };
        return;
      }
      const text = l.text || '';
      if (!text) return;
      if (!timeMap[l.time]) timeMap[l.time] = { time: l.time };
      delete timeMap[l.time].break;
      timeMap[l.time][lang] = text;
    });
  });
  return Object.values(timeMap).sort((a, b) => a.time - b.time);
}

/**
 * Detect available language keys from parsed lyrics array.
 */
export function detectLangs(parsedLines) {
  const langSet = new Set();
  parsedLines.forEach(l => {
    Object.keys(l).forEach(k => {
      if (k !== 'time' && k !== 'text' && k !== 'break') langSet.add(k);
    });
  });
  return [...langSet];
}
