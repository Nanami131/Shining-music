package org.L2.music.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 ffmpeg loudnorm 滤镜测量音频积分响度 (Integrated Loudness, LUFS)。
 * <p>
 * 使用 EBU R128 标准的两遍扫描第一遍输出，提取 {@code input_i} 字段。
 */
@Component
public class LufsAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(LufsAnalyzer.class);

    private static final Pattern INPUT_I_PATTERN =
            Pattern.compile("\"input_i\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * 测量 MultipartFile 的积分响度。
     *
     * @return LUFS 值；测量失败返回 null
     */
    public Float measure(MultipartFile file) {
        Path tmp = null;
        try {
            String suffix = extractSuffix(file.getOriginalFilename());
            tmp = Files.createTempFile("lufs_", suffix);
            Files.copy(file.getInputStream(), tmp, StandardCopyOption.REPLACE_EXISTING);
            return measureFile(tmp);
        } catch (Exception e) {
            log.warn("LUFS measurement failed: {}", e.getMessage());
            return null;
        } finally {
            deleteSilently(tmp);
        }
    }

    /**
     * 测量本地文件的积分响度。
     *
     * @return LUFS 值；测量失败返回 null
     */
    public Float measureFile(Path audioPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-hide_banner", "-nostats",
                    "-i", audioPath.toAbsolutePath().toString(),
                    "-af", "loudnorm=print_format=json",
                    "-f", "null", "/dev/null"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("ffmpeg exited with code {} for {}", exitCode, audioPath.getFileName());
                return null;
            }

            return parseInputI(output.toString());
        } catch (Exception e) {
            log.warn("LUFS measurement failed for {}: {}", audioPath.getFileName(), e.getMessage());
            return null;
        }
    }

    private Float parseInputI(String output) {
        Matcher matcher = INPUT_I_PATTERN.matcher(output);
        if (matcher.find()) {
            try {
                return Float.parseFloat(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse input_i value: {}", matcher.group(1));
            }
        }
        return null;
    }

    private static String extractSuffix(String filename) {
        if (filename == null) return ".tmp";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : ".tmp";
    }

    private static void deleteSilently(Path path) {
        if (path != null) {
            try { Files.deleteIfExists(path); } catch (Exception ignored) {}
        }
    }
}
