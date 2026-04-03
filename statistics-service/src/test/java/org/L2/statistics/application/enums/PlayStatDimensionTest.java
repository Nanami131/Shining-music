package org.L2.statistics.application.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PlayStatDimension 枚举测试")
class PlayStatDimensionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 1, 14, 30, 0);

    @Test
    @DisplayName("TODAY — 起始时间为当天零点")
    void todayStart() {
        LocalDateTime start = PlayStatDimension.TODAY.resolveStart(NOW);
        assertEquals(LocalDateTime.of(2026, 4, 1, 0, 0, 0), start);
    }

    @Test
    @DisplayName("WEEK — 起始时间为7天前")
    void weekStart() {
        LocalDateTime start = PlayStatDimension.WEEK.resolveStart(NOW);
        assertEquals(NOW.minusDays(7), start);
    }

    @Test
    @DisplayName("MONTH — 起始时间为30天前")
    void monthStart() {
        LocalDateTime start = PlayStatDimension.MONTH.resolveStart(NOW);
        assertEquals(NOW.minusDays(30), start);
    }

    @Test
    @DisplayName("TOTAL — 起始时间为null")
    void totalStart() {
        assertNull(PlayStatDimension.TOTAL.resolveStart(NOW));
    }

    @Test
    @DisplayName("resolveEnd — 所有维度结束时间均为当前时间")
    void resolveEnd() {
        for (PlayStatDimension dim : PlayStatDimension.values()) {
            assertEquals(NOW, dim.resolveEnd(NOW));
        }
    }

    @Test
    @DisplayName("from(null) — 默认返回WEEK")
    void fromNull() {
        assertEquals(PlayStatDimension.WEEK, PlayStatDimension.from(null));
    }

    @Test
    @DisplayName("from(空字符串) — 默认返回WEEK")
    void fromBlank() {
        assertEquals(PlayStatDimension.WEEK, PlayStatDimension.from(""));
        assertEquals(PlayStatDimension.WEEK, PlayStatDimension.from("   "));
    }

    @Test
    @DisplayName("from(合法值) — 忽略大小写正确解析")
    void fromValid() {
        assertEquals(PlayStatDimension.TODAY, PlayStatDimension.from("today"));
        assertEquals(PlayStatDimension.TODAY, PlayStatDimension.from("TODAY"));
        assertEquals(PlayStatDimension.MONTH, PlayStatDimension.from("month"));
        assertEquals(PlayStatDimension.TOTAL, PlayStatDimension.from("TOTAL"));
    }

    @Test
    @DisplayName("from(非法值) — 降级返回WEEK")
    void fromInvalid() {
        assertEquals(PlayStatDimension.WEEK, PlayStatDimension.from("YEAR"));
        assertEquals(PlayStatDimension.WEEK, PlayStatDimension.from("abc"));
    }
}
