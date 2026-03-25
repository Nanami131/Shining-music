package org.L2.statistics.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class UserEventLog {

    private Long id;
    private Long userId;
    private String eventType;
    private String targetType;
    private Long targetId;
    private String extraData;
    private LocalDateTime createdAt;
}
