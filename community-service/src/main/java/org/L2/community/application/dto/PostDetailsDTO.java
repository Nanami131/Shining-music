package org.L2.community.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子详情 DTO，包含正文和评论树。
 */
@Data
@Accessors(chain = true)
public class PostDetailsDTO {

    private Long id;

    private Long userId;

    private String title;

    private String content;

    private Integer commentCount;

    private LocalDateTime lastCommentAt;

    private LocalDateTime createdAt;

    /**
     * 一级评论列表，每个 CommentDTO 中再带 replies。
     */
    private List<CommentDTO> comments;
}
