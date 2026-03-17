package org.L2.community.application.request;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 创建评论请求。
 */
@Data
@Accessors(chain = true)
public class CommentCreateRequest {

    private Long postId;

    /**
     * 父评论ID：
     *  - 一级评论为空
     *  - 楼中楼回复时为被回复评论的ID
     */
    private Long parentId;

    private Long userId;

    private Long replyToUserId;

    private String content;
}
