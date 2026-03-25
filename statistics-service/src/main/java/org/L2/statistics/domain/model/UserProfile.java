package org.L2.statistics.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class UserProfile {

    private Long userId;
    private Integer totalPlayCount;
    private Integer totalPlayDuration;
    private BigDecimal avgCompletionRate;
    private String favLanguage;
    private Long topSingerId;
    private String topSingerName;
    private Integer activeHour;
    private BigDecimal dailyAvgPlays;
    private LocalDateTime lastPlayAt;
    private LocalDateTime updatedAt;
}
