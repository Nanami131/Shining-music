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
     * 一级评论时可以为空；
     * 楼中楼时可以用于显式指定所属楼层。
     *
     * TODO: 当前服务实现没有强依赖 rootId，后续按语义补充。
     */
    private Long rootId;

    /**
     * 父评论ID：
     *  - 一级评论为空
     *  - 楼中楼回复时为被回复评论的ID
     */
    private Long parentId;

    /**
     * 评论类型：1-楼(一级)，2-楼中楼。
     * TODO: 调用方根据约定传入，不做值校验。
     */
    private Byte commentType;

    private Long userId;

    private Long replyToUserId;

    private String content;

    /**
     * 评论状态，由调用方传入。
     * TODO: 与帖子状态枚举保持一致。
     */
    private Byte status;
}
