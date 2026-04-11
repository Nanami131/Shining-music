package org.L2.statistics.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.L2.common.R;
import org.L2.statistics.application.dto.AnnualReportDTO;
import org.L2.statistics.infrastructure.mapper.UserSongPlayRecordMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnualReportService {

    private final UserSongPlayRecordMapper mapper;
    private final ObjectMapper objectMapper;

    private static final String[] MOOD_NAMES = {
            "Calm", "Energetic", "Melancholic", "Joyful", "Tense", "Romantic"
    };
    private static final int[] MOOD_DIM_INDICES = {10, 11, 12, 13, 14, 15};

    private static final Map<String, String> LANG_LABELS;
    static {
        LANG_LABELS = new LinkedHashMap<>();
        LANG_LABELS.put("lang_ja", "日语");
        LANG_LABELS.put("lang_zh", "华语");
        LANG_LABELS.put("lang_en", "英语");
        LANG_LABELS.put("instrumental", "纯音乐");
    }

    public R generateAnnualReport(Long userId, int year) {
        if (userId == null || userId <= 0) {
            return R.error("无效的用户 ID");
        }

        LocalDateTime start = LocalDateTime.of(year, 1, 1, 0, 0, 0);
        LocalDateTime end = LocalDateTime.of(year + 1, 1, 1, 0, 0, 0);

        AnnualReportDTO dto = new AnnualReportDTO()
                .setYear(year)
                .setUserId(userId);

        Long playCount = mapper.countByUserAndTimeRange(userId, start, end);
        dto.setTotalPlayCount(playCount != null ? playCount : 0L);

        Long totalDuration = mapper.annualTotalDuration(userId, start, end);
        dto.setTotalListenDuration(totalDuration != null ? totalDuration : 0L);

        Long songCount = mapper.annualSongCount(userId, start, end);
        dto.setTotalSongCount(songCount != null ? songCount : 0L);

        dto.setTopSongs(buildTopSongs(userId, start, end));
        dto.setTopSingers(buildTopSingers(userId, start, end));

        dto.setMaxStreak(calcMaxStreak(userId, start, end));

        dto.setHourlyDistribution(buildHourlyDistribution(userId, start, end));
        dto.setMonthlyTrend(buildMonthlyTrend(userId, start, end));
        dto.setLanguageDistribution(buildLanguageDistribution(userId, start, end));

        Double avg = mapper.annualAvgCompletion(userId, start, end);
        dto.setAvgCompletionRate(avg != null ? Math.round(avg * 100.0) / 100.0 : null);

        List<AnnualReportDTO.TopSingerItem> singers = dto.getTopSingers();
        if (singers != null && !singers.isEmpty()) {
            dto.setFavoriteSinger(singers.get(0));
        }

        buildPreferenceData(userId, dto);

        return R.success("年度报告生成成功", dto);
    }

    private List<AnnualReportDTO.TopSongItem> buildTopSongs(Long userId, LocalDateTime start, LocalDateTime end) {
        List<Map<String, Object>> rows = mapper.annualTopSongs(userId, start, end, 5);
        if (rows == null) return Collections.emptyList();
        return rows.stream().map(row -> new AnnualReportDTO.TopSongItem()
                .setSongId(toLong(row.get("songId")))
                .setTitle((String) row.get("title"))
                .setSingerName((String) row.get("singerName"))
                .setCoverUrl((String) row.get("coverUrl"))
                .setPlayCount(toLong(row.get("playCount")))
                .setTotalDuration(toLong(row.get("totalDuration")))
        ).collect(Collectors.toList());
    }

    private List<AnnualReportDTO.TopSingerItem> buildTopSingers(Long userId, LocalDateTime start, LocalDateTime end) {
        List<Map<String, Object>> rows = mapper.annualTopSingers(userId, start, end, 5);
        if (rows == null) return Collections.emptyList();
        return rows.stream().map(row -> new AnnualReportDTO.TopSingerItem()
                .setSingerId(toLong(row.get("singerId")))
                .setSingerName((String) row.get("singerName"))
                .setAvatarUrl((String) row.get("avatarUrl"))
                .setPlayCount(toLong(row.get("playCount")))
                .setTotalDuration(toLong(row.get("totalDuration")))
        ).collect(Collectors.toList());
    }

    private int calcMaxStreak(Long userId, LocalDateTime start, LocalDateTime end) {
        List<String> dateStrs = mapper.annualPlayDates(userId, start, end);
        if (dateStrs == null || dateStrs.isEmpty()) return 0;

        List<LocalDate> dates = dateStrs.stream()
                .map(s -> LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE))
                .sorted()
                .collect(Collectors.toList());

        int maxStreak = 1, current = 1;
        for (int i = 1; i < dates.size(); i++) {
            if (dates.get(i).equals(dates.get(i - 1).plusDays(1))) {
                current++;
                maxStreak = Math.max(maxStreak, current);
            } else {
                current = 1;
            }
        }
        return maxStreak;
    }

    private List<Integer> buildHourlyDistribution(Long userId, LocalDateTime start, LocalDateTime end) {
        List<Map<String, Object>> rows = mapper.annualHourlyDistribution(userId, start, end);
        int[] hours = new int[24];
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                int h = ((Number) row.get("hour")).intValue();
                hours[h] = ((Number) row.get("cnt")).intValue();
            }
        }
        List<Integer> result = new ArrayList<>(24);
        for (int v : hours) result.add(v);
        return result;
    }

    private List<AnnualReportDTO.MonthlyPlayItem> buildMonthlyTrend(Long userId, LocalDateTime start, LocalDateTime end) {
        List<Map<String, Object>> rows = mapper.annualMonthlyTrend(userId, start, end);
        Map<Integer, Long> monthMap = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                int m = ((Number) row.get("month")).intValue();
                long c = ((Number) row.get("playCount")).longValue();
                monthMap.put(m, c);
            }
        }
        List<AnnualReportDTO.MonthlyPlayItem> result = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            result.add(new AnnualReportDTO.MonthlyPlayItem()
                    .setMonth(m)
                    .setPlayCount(monthMap.getOrDefault(m, 0L)));
        }
        return result;
    }

    private List<AnnualReportDTO.LanguageItem> buildLanguageDistribution(Long userId, LocalDateTime start, LocalDateTime end) {
        List<Map<String, Object>> rows = mapper.annualLanguageDistribution(userId, start, end);
        if (rows == null) return Collections.emptyList();
        return rows.stream().map(row -> {
            String name = (String) row.get("language");
            String label = LANG_LABELS.getOrDefault(name, name);
            return new AnnualReportDTO.LanguageItem()
                    .setLanguage(label)
                    .setSongCount(toLong(row.get("songCount")));
        }).collect(Collectors.toList());
    }

    private void buildPreferenceData(Long userId, AnnualReportDTO dto) {
        try {
            String vectorJson = mapper.annualPreferenceVector(userId);
            if (vectorJson == null || vectorJson.isBlank()) return;

            List<Double> vector = objectMapper.readValue(vectorJson, new TypeReference<>() {});
            dto.setPreferenceVector(vector);

            if (vector.size() > 15) {
                Map<String, Double> mood = new LinkedHashMap<>();
                for (int i = 0; i < MOOD_NAMES.length && i < MOOD_DIM_INDICES.length; i++) {
                    int idx = MOOD_DIM_INDICES[i];
                    mood.put(MOOD_NAMES[i], idx < vector.size() ? vector.get(idx) : 0.0);
                }
                dto.setMoodDistribution(mood);
            }

            dto.setMusicPersonality(deriveMusicPersonality(vector));
        } catch (Exception e) {
            log.warn("解析偏好向量失败 userId={}", userId, e);
        }
    }

    private String deriveMusicPersonality(List<Double> v) {
        if (v == null || v.size() < 28) return null;

        boolean ja = v.get(0) > 0.5;
        boolean zh = v.get(1) > 0.5;
        boolean anime = v.get(4) > 0.5;
        boolean vocaloid = v.get(6) > 0.5;

        double calm = v.get(10), energetic = v.get(11), melancholic = v.get(12);
        double joyful = v.get(13), romantic = v.get(15);

        boolean female = v.get(16) > 0.5;

        double era = v.size() > 27 ? v.get(27) : 0.5;
        boolean modern = era > 0.7;

        if (ja && anime && melancholic > 0.5) return "深夜番剧沉浸者";
        if (ja && energetic > 0.6) return "二次元能量站";
        if (ja && calm > 0.5 && romantic > 0.4) return "夜行浪漫派";
        if (vocaloid) return "电子音色探索者";
        if (zh && joyful > 0.5) return "华语快乐星球";
        if (zh && melancholic > 0.5) return "华语深情物语";
        if (calm > 0.6 && female) return "治愈系女声守护者";
        if (energetic > 0.6 && modern) return "潮流节奏猎手";
        if (melancholic > 0.5) return "雨夜独行诗人";
        if (romantic > 0.5) return "浪漫主义收藏家";
        if (ja) return "和风音乐旅人";
        if (zh) return "华语乐坛漫步者";
        return "多元音乐探索者";
    }

    private static Long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        return Long.parseLong(obj.toString());
    }
}
