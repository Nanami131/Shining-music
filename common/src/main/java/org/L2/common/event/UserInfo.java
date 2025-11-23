package org.L2.common.event;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 用户维度信息。
 *
 * 用于标识是谁产生了该事件，后续可以扩展很多用户相关信息
 */
@Data
@Accessors(chain = true)
public class UserInfo {

    private Long userId;
    private String sessionId; // 用于标识用户所使用的会话 暂时没有使用
}
