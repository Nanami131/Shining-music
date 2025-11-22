package org.L2.community.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 帖子实体，对应 posts 表。
 */
@Data
@Accessors(chain = true)
public class ForumPost {
    private Long id;
    private Long userId;
    private String title;
    private String content;

    /**
     * 帖子状态（正常、删除、审核中等）
     * 具体取值请在业务中约定。TODO: 与实际状态枚举保持一致
     */
    private Byte status;
    private Integer commentCount; //评论总数
    private LocalDateTime lastCommentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
