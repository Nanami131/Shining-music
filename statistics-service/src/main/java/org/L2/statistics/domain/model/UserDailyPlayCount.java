package org.L2.statistics.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 用户每日听歌次数聚合结果（领域对象）。
 */
@Data
@Accessors(chain = true)
public class UserDailyPlayCount {

    private LocalDate statDate;
    private Long playCount;
}
