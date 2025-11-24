package org.L2.statistics.application.enums;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 播放统计的时间维度。
 */
public enum PlayStatDimension {
    TODAY {
        @Override
        public LocalDateTime resolveStart(LocalDateTime now) {
            return now.toLocalDate().atStartOfDay();
        }
    },
    WEEK {
        @Override
        public LocalDateTime resolveStart(LocalDateTime now) {
            return now.minusDays(7);
        }
    },
    MONTH {
        @Override
        public LocalDateTime resolveStart(LocalDateTime now) {
            return now.minusDays(30);
        }
    },
    TOTAL {
        @Override
        public LocalDateTime resolveStart(LocalDateTime now) {
            return null;
        }
    };

    /**
     * 根据当前时间计算起始时间。
     *
     * @param now 当前时间
     * @return 起始时间，TOTAL 返回 null
     */
    public abstract LocalDateTime resolveStart(LocalDateTime now);

    /**
     * 默认结束时间为当前时间。
     */
    public LocalDateTime resolveEnd(LocalDateTime now) {
        return now;
    }

    /**
     * 将字符串解析为时间维度，默认为 WEEK。
     *
     * @param raw 传入的字符串
     * @return 匹配的枚举
     */
    public static PlayStatDimension from(String raw) {
        if (raw == null || raw.isBlank()) {
            return WEEK;
        }
        try {
            return PlayStatDimension.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return WEEK;
        }
    }
}
