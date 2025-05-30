package org.L2.community.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 统一评论实体，涵盖歌曲、歌单和帖子评论
 */
@Data
@Accessors(chain = true)
public class Comment {
    private Long id;
    private Long userId;
    private String content;
    private String targetType; // 目标类型（"song", "playlist", "post"）
    private Long targetId; // 目标ID（歌曲、歌单或帖子ID）
    private Long parentCommentId;
    private Long likes;
    private Byte status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}