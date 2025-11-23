package org.L2.common.event;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 播放相关信息。
 * 目前只记录歌曲ID，后续可以在此扩展专辑、歌单、播放模式等字段。
 */
@Data
@Accessors(chain = true)
public class PlaybackInfo {

    private Long songId;
}
