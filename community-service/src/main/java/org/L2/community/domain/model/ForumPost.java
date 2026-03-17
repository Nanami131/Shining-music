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
     * 帖子状态：0-正常，1-删除，2-审核中
     */
    private Byte status;
    private Integer commentCount; //评论总数
    private LocalDateTime lastCommentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
