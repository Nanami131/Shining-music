package org.L2.statistics.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class AnnualReportDTO {

    private int year;
    private Long userId;

    private Long totalListenDuration;
    private Long totalSongCount;
    private Long totalPlayCount;

    private List<TopSongItem> topSongs;
    private List<TopSingerItem> topSingers;

    private int maxStreak;
    private List<Integer> hourlyDistribution;
    private List<MonthlyPlayItem> monthlyTrend;

    private List<LanguageItem> languageDistribution;

    private Double avgCompletionRate;

    private TopSingerItem favoriteSinger;

    private List<Double> preferenceVector;
    private Map<String, Double> moodDistribution;
    private String musicPersonality;

    @Data
    @Accessors(chain = true)
    public static class TopSongItem {
        private Long songId;
        private String title;
        private String singerName;
        private String coverUrl;
        private Long playCount;
        private Long totalDuration;
    }

    @Data
    @Accessors(chain = true)
    public static class TopSingerItem {
        private Long singerId;
        private String singerName;
        private String avatarUrl;
        private Long playCount;
        private Long totalDuration;
    }

    @Data
    @Accessors(chain = true)
    public static class MonthlyPlayItem {
        private int month;
        private Long playCount;
    }

    @Data
    @Accessors(chain = true)
    public static class LanguageItem {
        private String language;
        private Long songCount;
    }
}
