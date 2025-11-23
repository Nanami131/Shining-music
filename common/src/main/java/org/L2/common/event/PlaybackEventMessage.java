package org.L2.common.event;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 播放事件消息顶层结构。
 *
 * 当前只包含 event（事件元信息） 和 user（用户上下文），
 * 后续可以在此类中扩展其他上下文（例如 device、network、recommend、playback）。
 */
@Data
@Accessors(chain = true)
public class PlaybackEventMessage {
    private EventInfo event; // 事件元信息
    private UserInfo user; // 用户元信息
}
