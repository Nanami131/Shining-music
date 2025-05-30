package org.L2.community.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 论坛帖子实体
 */
@Data
@Accessors(chain = true)
public class Post {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private Long likes;
    private Byte status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}