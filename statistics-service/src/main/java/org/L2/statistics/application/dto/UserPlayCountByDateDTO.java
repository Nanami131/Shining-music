package org.L2.statistics.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 用户听歌次数 DTO。
 */
@Data
@Accessors(chain = true)
public class UserPlayCountByDateDTO {

    private LocalDate statDate;
    private Long playCount;
}
