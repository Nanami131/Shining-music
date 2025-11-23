package org.L2.statistics.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 用户听歌播放记录实体。
 * 每消费到一条播放事件，就插入一条记录。
 */
@Data
@Accessors(chain = true)
public class UserSongPlayRecord {

    private Long id;
    private Long userId;
    private Long songId;
    private LocalDateTime playedAt;
}
