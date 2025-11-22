package org.L2.community.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 评论 DTO，支持两级嵌套。
 */
@Data
@Accessors(chain = true)
public class CommentDTO {

    private Long id;
    private Long postId;
    private Long parentId;
    private Long userId;
    private Long replyToUserId;
    private String content;
    private Integer floorNo;
    private Integer replyCount;
    private LocalDateTime createdAt;
    private List<CommentDTO> replies = new ArrayList<>();
}
