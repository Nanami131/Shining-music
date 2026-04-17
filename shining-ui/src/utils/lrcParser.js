/**
 * Shared LRC parser for SongDetail and BottomBar.
 * Supports: [mm:ss.xx], [mm:ss], [mm:ss.xxx], extended [time][lang]text
 */

const TIME_RE = /^\[(\d+:\d+(?:\.\d+)?)\]/;
const EXTENDED_RE = /^\[(\d+:\d+(?:\.\d+)?)\]\[([^\]]+)\](.*)$/;
const STANDARD_RE = /^\[(\d+:\d+(?:\.\d+)?)\](.*)$/;
const META_RE = /^(作词|作曲|编曲|作詞|作曲|編曲|ar|ti|al|by|offset|length|tool|ve|re)\s*[:：]/i;
const BRACKET_META_RE = /^\[(ar|ti|al|by|offset|length|tool|ve|re):.*\]$/i;

const LANG_ALIASES = {
  zh: 'zh',
  cn: 'zh',
  chi: 'zh',
  zho: 'zh',
  chinese: 'zh',
  mandarin: 'zh',
  'zh-cn': 'zh',
  'zh-hans': 'zh',
  'zh-hant': 'zh',
  '中文': 'zh',
  '汉语': 'zh',
  '漢語': 'zh',
  ja: 'ja',
  jp: 'ja',
  jpn: 'ja',
  japanese: 'ja',
  '日语': 'ja',
  '日文': 'ja',
  '日本語': 'ja',
  en: 'en',
  eng: 'en',
  english: 'en',
  '英语': 'en',
  '英文': 'en',
};

const LANG_LABELS = {
  zh: '中',
  ja: '日',
  en: '英',
};

export function timeToSeconds(timeStr) {
  const [min, sec] = timeStr.split(':').map(parseFloat);
  return min * 60 + sec;
}

export function normalizeLyricLang(lang) {
  if (lang === null || lang === undefined) return '';
  const raw = String(lang).trim();
  if (!raw) return '';
  const lower = raw.toLowerCase();
  return LANG_ALIASES[lower] || LANG_ALIASES[raw] || lower;
}

export function lyricLangLabel(lang) {
  const normalized = normalizeLyricLang(lang);
  return LANG_LABELS[normalized] || String(lang || '版本');
}

export function orderLyricLangs(langs) {
  const unique = [];
  langs
    .map(normalizeLyricLang)
    .filter(Boolean)
    .forEach(lang => {
      if (!unique.includes(lang)) unique.push(lang);
    });

  return unique.sort((a, b) => {
    if (a === 'zh' && b !== 'zh') return 1;
    if (b === 'zh' && a !== 'zh') return -1;
    return 0;
  });
}

function isMetadataText(text) {
  return META_RE.test(text) || BRACKET_META_RE.test(text);
}

function hasFiniteTime(line) {
  return Number.isFinite(line.time);
}

export function parseLyrics(content) {
  if (!content) return [];

  const lines = content.replace(/^\uFEFF/, '').split(/\r?\n/).map(l => l.trim());
  const timeMap = {};
  const plainLines = [];

  lines.forEach(line => {
    if (!line) return;

    if (!TIME_RE.test(line)) {
      if (!isMetadataText(line)) {
        plainLines.push(line);
      }
      return;
    }

    const langMatch = line.match(EXTENDED_RE);
    if (langMatch) {
      const [, time, lang, text] = langMatch;
      const trimmed = text.trim();
      if (!trimmed || isMetadataText(trimmed)) return;
      const t = timeToSeconds(time);
      if (!timeMap[t]) timeMap[t] = { time: t };
      timeMap[t][normalizeLyricLang(lang)] = trimmed;
      return;
    }

    const stdMatch = line.match(STANDARD_RE);
    if (stdMatch) {
      const [, time, text] = stdMatch;
      const trimmed = (text || '').trim();
      if (isMetadataText(trimmed)) return;
      const t = timeToSeconds(time);
      if (!timeMap[t]) timeMap[t] = { time: t };
      if (trimmed) {
        timeMap[t].text = trimmed;
      } else if (!timeMap[t].text && !timeMap[t].ja && !timeMap[t].zh && !timeMap[t].en) {
        timeMap[t].break = true;
      }
    }
  });

  const timedLines = Object.values(timeMap).sort((a, b) => a.time - b.time);
  if (timedLines.length) {
    return timedLines.concat(plainLines.map(text => ({ text })));
  }
  return plainLines.map(text => ({ text }));
}

/**
 * Merge multiple parsed lyric arrays into multilingual entries.
 * @param {Array<{lang: string, lines: Array}>} sources - [{lang:'ja', lines: [...]}, {lang:'zh', lines: [...]}]
 * @returns {Array<{time: number, langs: {[lang]: string}}>}
 */
export function mergeMultiLang(sources) {
  const timeMap = {};
  sources.forEach(({ lang, lines }) => {
    const normalizedLang = normalizeLyricLang(lang);
    if (!normalizedLang) return;

    lines.forEach((l, index) => {
      const hasTime = hasFiniteTime(l);
      const key = hasTime ? `t:${l.time}` : `i:${index}`;
      if (l.break) {
        if (!timeMap[key]) {
          timeMap[key] = hasTime
            ? { time: l.time, _order: index, break: true }
            : { _order: index, break: true };
        }
        return;
      }
      const text = l[normalizedLang] || l.text || '';
      if (!text) return;
      if (!timeMap[key]) {
        timeMap[key] = hasTime ? { time: l.time, _order: index } : { _order: index };
      }
      delete timeMap[key].break;
      timeMap[key][normalizedLang] = text;
    });
  });
  return Object.values(timeMap)
    .sort((a, b) => {
      const aHasTime = hasFiniteTime(a);
      const bHasTime = hasFiniteTime(b);
      if (aHasTime && bHasTime) return a.time - b.time;
      if (aHasTime !== bHasTime) return aHasTime ? -1 : 1;
      return a._order - b._order;
    })
    .map(line => {
      const { _order, ...clean } = line;
      return clean;
    });
}

/**
 * Detect available language keys from parsed lyrics array.
 */
export function detectLangs(parsedLines) {
  const langSet = new Set();
  parsedLines.forEach(l => {
    Object.keys(l).forEach(k => {
      if (k !== 'time' && k !== 'text' && k !== 'break') {
        const normalized = normalizeLyricLang(k);
        if (normalized) langSet.add(normalized);
      }
    });
  });
  return [...langSet];
}
