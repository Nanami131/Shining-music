package org.L2.community.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class PostLike {
    private Long id;
    private Long postId;
    private Long userId;
    private LocalDateTime createdAt;
}
