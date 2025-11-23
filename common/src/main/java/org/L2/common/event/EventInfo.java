package org.L2.common.event;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 事件元信息。
 *
 * 用于描述“发生了什么事件”，方便按类别路由、按版本兼容。
 */
@Data
@Accessors(chain = true)
public class EventInfo {
    private String eventId;
    private String eventCategory;
    private String eventName;
    private LocalDateTime occurredAt;
    private String traceId;
}
