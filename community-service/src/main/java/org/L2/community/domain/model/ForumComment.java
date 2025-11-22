package org.L2.community.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 帖子评论实体，对应 post_comments 表。
 * 区分一级评论（楼）和二级评论（楼中楼）。
 * 注意：不修改已有的 Comment 类，本类单独用于新版帖子评论设计。
 */
@Data
@Accessors(chain = true)
public class ForumComment {
    private Long id;
    private Long postId;
    /**
     * 所属楼ID：
     *  - 一级评论时等于自身ID
     *  - 楼中楼为所属楼的ID
     *
     * TODO: 当前未在业务逻辑中填充 rootId，后续根据需要完善
     */
    private Long rootId;
    /**
     * 父评论ID：
     *  - 一级评论为空
     *  - 楼中楼为被回复的评论ID
     */
    private Long parentId;

    /**
     * 评论类型：1-楼(一级)，2-楼中楼
     * TODO: 建议和枚举或常量统一管理
     */
    private Byte commentType;
    private Long userId;

    /**
     * 被回复用户ID，方便展示 @谁
     */
    private Long replyToUserId;
    private String content;

    /**
     * 楼层号，仅一级评论使用，从1开始递增
     * TODO: 当前未自动维护楼层号，需要后续实现
     */
    private Integer floorNo;

    /**
     * 回复数量，主要用于一级评论
     * TODO: 当前未自动维护回复数量，需要后续实现
     */
    private Integer replyCount;
    /**
     * 评论状态（正常、删除、审核中等）
     */
    private Byte status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
