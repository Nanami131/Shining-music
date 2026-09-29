package org.L2.music.application.request;

import lombok.Data;

/** 播放开始上报；playedAt 为客户端播放开始时的 Unix 毫秒时间戳。 */
@Data
public class PlayStartRequest {
    private Long songId;
    private String playSessionId;
    private Long playedAt;
}
