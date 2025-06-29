package org.L2.music.domain.service;

import org.L2.common.R;
import org.L2.music.domain.model.Lyrics;
import org.L2.music.infrastructure.LyricsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class LyricsService {
    @Autowired
    private LyricsMapper lyricsMapper;

    public R uploadLyrics(Long songId, MultipartFile file,String msg) {
        if (songId == null || songId <= 0) {
            return R.error("无效的歌曲ID");
        }
        if (file == null || file.isEmpty()) {
            return R.error("文件不能为空");
        }

        // 验证文件是否为 LRC 格式
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".lrc")) {
            return R.error("文件不是 LRC 格式");
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            boolean isLrc = false;
            Pattern timeStampPattern = Pattern.compile("\\[\\d{2}:\\d{2}\\.\\d{2,3}\\]");

            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n"); // 保留换行符
                if (timeStampPattern.matcher(line).find()) {
                    isLrc = true;
                }
            }

            if (!isLrc) {
                return R.error("文件内容不符合 LRC 格式");
            }
            LocalDateTime now =LocalDateTime.now();
            lyricsMapper.insert(new Lyrics().setSongId(songId).
                    setContent(content.toString()).setLanguageMsg(msg)
                    .setCreatedAt(now).setCreatedAt(now)
            );
            return R.success("歌词上传成功");
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
