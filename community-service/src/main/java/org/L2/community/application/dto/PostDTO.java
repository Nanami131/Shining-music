package org.L2.community.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 帖子列表用 DTO。
 */
@Data
@Accessors(chain = true)
public class PostDTO {

    private Long id;
    private Long userId;
    private String title;
    private Integer commentCount;
    private LocalDateTime lastCommentAt;
    private LocalDateTime createdAt;
}
