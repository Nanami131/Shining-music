package org.L2.music.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LrcParser 单元测试")
class LrcParserTest {

    private LrcParser parser;

    @BeforeEach
    void setUp() {
        parser = new LrcParser();
    }

    @Nested
    @DisplayName("parse() - 标准LRC解析")
    class ParseTests {

        @Test
        @DisplayName("标准LRC格式 — 正确解析时间戳和歌词文本")
        void standardLrc() {
            String lrc = """
                    [00:12.34]第一行歌词
                    [00:24.56]第二行歌词
                    [00:36.78]第三行歌词
                    """;
            List<LrcParser.LyricsVersion> versions = parser.parse(lrc, "zh");
            assertEquals(1, versions.size());
            LrcParser.LyricsVersion v = versions.get(0);
            assertEquals("zh", v.getLang());
            assertTrue(v.getNormalizedLrc().contains("[00:12.34]第一行歌词"));
            assertTrue(v.getPlainText().contains("第一行歌词"));
            assertFalse(v.getPlainText().contains("[00:12.34]"));
        }

        @Test
        @DisplayName("双语LRC — 按语言标签拆分为多个版本")
        void bilingualLrc() {
            String lrc = """
                    [00:05.00][zh]你好世界
                    [00:05.00][ja]こんにちは世界
                    [00:10.00][zh]再见
                    [00:10.00][ja]さようなら
                    """;
            List<LrcParser.LyricsVersion> versions = parser.parse(lrc, "zh");
            assertEquals(2, versions.size());
            assertTrue(versions.stream().anyMatch(v -> "zh".equals(v.getLang())));
            assertTrue(versions.stream().anyMatch(v -> "ja".equals(v.getLang())));
        }

        @Test
        @DisplayName("多时间戳行 — 同一歌词关联多个时间点")
        void multiTimestampLine() {
            String lrc = "[00:12.34][02:45.67]副歌歌词";
            List<LrcParser.LyricsVersion> versions = parser.parse(lrc, "zh");
            assertEquals(1, versions.size());
            String normalized = versions.get(0).getNormalizedLrc();
            assertTrue(normalized.contains("[00:12.34]副歌歌词"));
            assertTrue(normalized.contains("[02:45.67]副歌歌词"));
        }

        @Test
        @DisplayName("元数据行 — 自动跳过 [ti:] [ar:] 等标签")
        void metadataSkipped() {
            String lrc = """
                    [ti:歌曲名]
                    [ar:歌手名]
                    [al:专辑名]
                    [00:05.00]实际歌词
                    """;
            List<LrcParser.LyricsVersion> versions = parser.parse(lrc, "zh");
            assertEquals(1, versions.size());
            assertEquals("实际歌词", versions.get(0).getPlainText());
        }

        @Test
        @DisplayName("UTF-8 BOM — 正确去除BOM头")
        void bomHandling() {
            String lrc = "\uFEFF[00:05.00]BOM歌词";
            List<LrcParser.LyricsVersion> versions = parser.parse(lrc, "en");
            assertEquals(1, versions.size());
            assertEquals("BOM歌词", versions.get(0).getPlainText());
        }

        @Test
        @DisplayName("纯文本歌词 — 无时间戳时仅返回plainText")
        void plainTextOnly() {
            String lrc = """
                    第一行
                    第二行
                    第三行
                    """;
            List<LrcParser.LyricsVersion> versions = parser.parse(lrc, "zh");
            assertEquals(1, versions.size());
            assertTrue(versions.get(0).getNormalizedLrc().isEmpty());
            assertTrue(versions.get(0).getPlainText().contains("第一行"));
        }

        @Test
        @DisplayName("null输入 — 返回空列表")
        void nullInput() {
            List<LrcParser.LyricsVersion> versions = parser.parse(null, "zh");
            assertTrue(versions.isEmpty());
        }

        @Test
        @DisplayName("空字符串输入 — 返回空列表")
        void emptyInput() {
            List<LrcParser.LyricsVersion> versions = parser.parse("", "zh");
            assertTrue(versions.isEmpty());
        }

        @Test
        @DisplayName("仅空白字符 — 返回空列表")
        void blankInput() {
            List<LrcParser.LyricsVersion> versions = parser.parse("   \n\n  ", "zh");
            assertTrue(versions.isEmpty());
        }

        @Test
        @DisplayName("仅含元数据无歌词 — 返回空列表")
        void metadataOnly() {
            String lrc = "[ti:Test]\n[ar:Artist]\n[al:Album]";
            List<LrcParser.LyricsVersion> versions = parser.parse(lrc, "zh");
            assertTrue(versions.isEmpty());
        }

        @Test
        @DisplayName("混合有无时间戳行 — 全部正确归入对应版本")
        void mixedTimestampAndPlain() {
            String lrc = """
                    [00:05.00]有时间戳
                    纯文本行
                    """;
            List<LrcParser.LyricsVersion> versions = parser.parse(lrc, "zh");
            assertEquals(1, versions.size());
            assertTrue(versions.get(0).getPlainText().contains("有时间戳"));
            assertTrue(versions.get(0).getPlainText().contains("纯文本行"));
        }

        @Test
        @DisplayName("两位毫秒时间戳 — [00:12.34] 正确解析")
        void twoDigitMillis() {
            String lrc = "[01:30.45]测试";
            List<LrcParser.LyricsVersion> versions = parser.parse(lrc, "zh");
            assertEquals(1, versions.size());
            assertTrue(versions.get(0).getNormalizedLrc().contains("[01:30.45]测试"));
        }

        @Test
        @DisplayName("三位毫秒时间戳 — [00:12.345] 正确解析")
        void threeDigitMillis() {
            String lrc = "[01:30.456]测试";
            List<LrcParser.LyricsVersion> versions = parser.parse(lrc, "zh");
            assertEquals(1, versions.size());
            assertTrue(versions.get(0).getNormalizedLrc().contains("[01:30.456]测试"));
        }

        @Test
        @DisplayName("Windows换行符 — \\r\\n 正确处理")
        void windowsLineEndings() {
            String lrc = "[00:05.00]第一行\r\n[00:10.00]第二行\r\n";
            List<LrcParser.LyricsVersion> versions = parser.parse(lrc, "zh");
            assertEquals(1, versions.size());
            assertTrue(versions.get(0).getPlainText().contains("第一行"));
            assertTrue(versions.get(0).getPlainText().contains("第二行"));
        }
    }

    @Nested
    @DisplayName("hasTimestamps()")
    class HasTimestampsTests {

        @Test
        @DisplayName("包含时间戳 — 返回true")
        void withTimestamps() {
            assertTrue(parser.hasTimestamps("[00:12.34]歌词"));
        }

        @Test
        @DisplayName("纯文本 — 返回false")
        void withoutTimestamps() {
            assertFalse(parser.hasTimestamps("纯文本歌词"));
        }

        @Test
        @DisplayName("null — 返回false")
        void nullInput() {
            assertFalse(parser.hasTimestamps(null));
        }
    }

    @Nested
    @DisplayName("toPlainText() - 静态方法")
    class ToPlainTextTests {

        @Test
        @DisplayName("标准LRC — 去掉时间戳保留文本")
        void stripTimestamps() {
            String lrc = "[00:05.00]你好\n[00:10.00]世界";
            String plain = LrcParser.toPlainText(lrc);
            assertEquals("你好\n世界", plain);
        }

        @Test
        @DisplayName("含元数据和语言标签 — 全部去掉")
        void stripMetadataAndLangTags() {
            String lrc = "[ti:Test]\n[00:05.00][zh]中文歌词\n[00:05.00][ja]日本語歌詞";
            String plain = LrcParser.toPlainText(lrc);
            assertTrue(plain.contains("中文歌词"));
            assertTrue(plain.contains("日本語歌詞"));
            assertFalse(plain.contains("[ti:"));
            assertFalse(plain.contains("[zh]"));
        }

        @Test
        @DisplayName("null输入 — 返回空字符串")
        void nullInput() {
            assertEquals("", LrcParser.toPlainText(null));
        }

        @Test
        @DisplayName("空字符串 — 返回空字符串")
        void emptyInput() {
            assertEquals("", LrcParser.toPlainText(""));
        }
    }
}
