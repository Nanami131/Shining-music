package org.L2.common.mq;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PlayRecordMessage {

    /**
     * 触发播放的用户 ID
     */
    private Long userId;
}

