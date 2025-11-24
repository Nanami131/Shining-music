package org.L2.statistics.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 用户在某时间范围内的歌曲播放次数。
 */
@Data
@Accessors(chain = true)
public class UserTopSong {

    private Long songId;
    private Long playCount;
}
