package org.L2.music.domain.service;

import org.L2.common.R;
import org.L2.music.domain.model.Lyrics;
import org.L2.music.infrastructure.LyricsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LyricsService {
    @Autowired
    private LyricsMapper lyricsMapper;

    private final LrcParser lrcParser = new LrcParser();

    /**
     * 上传歌词文件。
     * 支持 .lrc 和 .txt 格式，支持标准 LRC、扩展双语 LRC、纯文本。
     * 如果文件包含语言标签（如 [zh]、[ja]），自动按语言拆分为多条记录。
     *
     * @param songId 歌曲 ID
     * @param file   歌词文件
     * @param lang   默认语言代码（zh/ja/en 等），当文件无语言标签时使用
     */
    @Transactional
    public R uploadLyrics(Long songId, MultipartFile file, String lang) {
        if (songId == null || songId <= 0) {
            return R.error("无效的歌曲ID");
        }
        if (file == null || file.isEmpty()) {
            return R.error("文件不能为空");
        }
        if (lang == null || lang.isBlank()) {
            return R.error("语言代码不能为空");
        }

        String filename = file.getOriginalFilename();
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (!lower.endsWith(".lrc") && !lower.endsWith(".txt")) {
                return R.error("仅支持 .lrc 和 .txt 格式");
            }
        }

        try {
            String rawContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<LrcParser.LyricsVersion> versions = lrcParser.parse(rawContent, lang.trim().toLowerCase());

            if (versions.isEmpty()) {
                return R.error("歌词文件无有效内容");
            }

            LocalDateTime now = LocalDateTime.now();
            int count = 0;
            for (LrcParser.LyricsVersion version : versions) {
                String content = version.getNormalizedLrc().isEmpty()
                        ? version.getPlainText()
                        : version.getNormalizedLrc();

                lyricsMapper.deleteBySongIdAndLang(songId, version.getLang());

                Lyrics lyrics = new Lyrics()
                        .setSongId(songId)
                        .setLanguageMsg(version.getLang())
                        .setContent(content)
                        .setCreatedAt(now)
                        .setUpdatedAt(now);
                lyricsMapper.insert(lyrics);
                count++;
            }

            String msg = count == 1
                    ? "歌词上传成功（" + versions.get(0).getLang() + "）"
                    : "歌词上传成功，检测到 " + count + " 种语言版本";
            return R.success(msg);
        } catch (Exception e) {
            return R.error("歌词文件解析失败: " + e.getMessage());
        }
    }

    public R getLyrics(Long lyricsId) {
        try {
            Lyrics lyrics = lyricsMapper.selectById(lyricsId);
            if (lyrics == null) {
                return R.error("歌词不存在");
            }
            return R.success("获取歌词成功", lyrics);
        }catch (Exception e) {
            return R.error("获取歌词失败"+e.getMessage());
        }
    }

    public R getAllLyricsBySongId(Long songId) {
        try {
            List<Lyrics> lyrics = lyricsMapper.selectBySongId(songId);
            if (lyrics == null||lyrics.isEmpty()) {
                return R.error("歌词不存在");
            }
            return R.success("获取歌词成功", lyrics);
        }catch (Exception e) {
            return R.error("获取歌词失败"+e.getMessage());
        }
    }
}
