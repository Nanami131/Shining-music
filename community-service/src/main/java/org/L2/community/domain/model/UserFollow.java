package org.L2.community.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class UserFollow {
    private Long id;
    private Long followerId;
    private Long followingId;
    private LocalDateTime createdAt;
}
