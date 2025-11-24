package org.L2.statistics.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 用户最常播放的歌曲 DTO。
 */
@Data
@Accessors(chain = true)
public class UserTopSongDTO {

    private Long songId;
    private Long playCount;
}
