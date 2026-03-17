package org.L2.music.domain.service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用 LRC 歌词解析器。
 * <p>
 * 支持的格式：
 * <ul>
 *   <li>标准 LRC: {@code [00:12.34]歌词内容}</li>
 *   <li>扩展双语: {@code [00:12.34][zh]中文歌词}</li>
 *   <li>多时间戳: {@code [00:12.34][02:45.67]副歌歌词}</li>
 *   <li>元数据行: {@code [ti:歌名] [ar:歌手]} （自动跳过）</li>
 *   <li>纯文本: 无时间戳的逐行歌词</li>
 *   <li>UTF-8 BOM 开头的文件</li>
 * </ul>
 * <p>
 * 当检测到行内语言标签 {@code [zh]}, {@code [ja]}, {@code [en]} 等时，
 * 自动按语言拆分为多个版本。
 */
public class LrcParser {

    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})]");

    private static final Pattern LANG_TAG_PATTERN =
            Pattern.compile("\\[([a-zA-Z]{2,3})]");

    private static final Pattern METADATA_PATTERN =
            Pattern.compile("^\\[([a-zA-Z]{2,}):(.*)\\]\\s*$");

    private static final String BOM = "\uFEFF";

    /**
     * 单语言解析结果。
     */
    public static class LyricsVersion {
        private final String lang;
        private final String normalizedLrc;
        private final String plainText;

        public LyricsVersion(String lang, String normalizedLrc, String plainText) {
            this.lang = lang;
            this.normalizedLrc = normalizedLrc;
            this.plainText = plainText;
        }

        public String getLang() { return lang; }
        /** 标准化 LRC（保留时间戳，供播放同步） */
        public String getNormalizedLrc() { return normalizedLrc; }
        /** 纯文本（去掉所有标签，供 ES 搜索索引） */
        public String getPlainText() { return plainText; }
    }

    /**
     * 解析 LRC 内容。
     *
     * @param rawContent  原始 LRC 文件内容
     * @param defaultLang 默认语言代码（当文件内无语言标签时使用）
     * @return 按语言拆分的歌词版本列表（至少一个）
     */
    public List<LyricsVersion> parse(String rawContent, String defaultLang) {
        if (rawContent == null || rawContent.isBlank()) {
            return List.of();
        }

        if (rawContent.startsWith(BOM)) {
            rawContent = rawContent.substring(1);
        }

        String[] lines = rawContent.split("\\r?\\n");

        Map<String, StringBuilder> langNormalized = new LinkedHashMap<>();
        Map<String, StringBuilder> langPlainText = new LinkedHashMap<>();
        boolean hasLangTags = false;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (METADATA_PATTERN.matcher(line).matches()) continue;

            List<String> timestamps = extractTimestamps(line);
            String remaining = TIMESTAMP_PATTERN.matcher(line).replaceAll("").trim();

            Matcher langMatcher = LANG_TAG_PATTERN.matcher(remaining);
            String detectedLang = null;
            if (langMatcher.find() && langMatcher.start() == 0) {
                detectedLang = langMatcher.group(1).toLowerCase();
                remaining = remaining.substring(langMatcher.end()).trim();
                hasLangTags = true;
            }

            String text = remaining;
            if (text.isEmpty()) continue;

            String lang = detectedLang != null ? detectedLang : defaultLang;

            langPlainText.computeIfAbsent(lang, k -> new StringBuilder())
                    .append(text).append("\n");

            if (!timestamps.isEmpty()) {
                StringBuilder normalizedBuf = langNormalized.computeIfAbsent(lang, k -> new StringBuilder());
                for (String ts : timestamps) {
                    normalizedBuf.append(ts).append(text).append("\n");
                }
            }
        }

        if (langPlainText.isEmpty()) {
            return List.of();
        }

        List<LyricsVersion> versions = new ArrayList<>();
        for (String lang : langPlainText.keySet()) {
            String plain = langPlainText.get(lang).toString().trim();
            String normalized = langNormalized.containsKey(lang)
                    ? langNormalized.get(lang).toString().trim()
                    : "";
            versions.add(new LyricsVersion(lang, normalized, plain));
        }

        return versions;
    }

    /**
     * 从单行中提取所有时间戳字符串。
     */
    private List<String> extractTimestamps(String line) {
        List<String> timestamps = new ArrayList<>();
        Matcher matcher = TIMESTAMP_PATTERN.matcher(line);
        while (matcher.find()) {
            timestamps.add(matcher.group());
        }
        return timestamps;
    }

    /**
     * 检查内容是否包含有效的 LRC 时间戳。
     */
    public boolean hasTimestamps(String content) {
        if (content == null) return false;
        return TIMESTAMP_PATTERN.matcher(content).find();
    }

    /**
     * 从已存储的 LRC 内容中提取纯文本（去掉时间戳和元数据行）。
     * 用于 ES 搜索索引。
     */
    public static String toPlainText(String lrcContent) {
        if (lrcContent == null || lrcContent.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : lrcContent.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (METADATA_PATTERN.matcher(trimmed).matches()) continue;
            String stripped = TIMESTAMP_PATTERN.matcher(trimmed).replaceAll("").trim();
            stripped = LANG_TAG_PATTERN.matcher(stripped).replaceAll("").trim();
            if (!stripped.isEmpty()) {
                sb.append(stripped).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
