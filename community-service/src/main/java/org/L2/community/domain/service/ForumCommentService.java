package org.L2.community.domain.service;

import org.L2.common.R;
import org.L2.common.annotation.AutoFill;
import org.L2.common.constant.OperationType;
import org.L2.community.domain.model.ForumComment;
import org.L2.community.domain.model.ForumPost;
import org.L2.community.infrastructure.mapper.ForumCommentMapper;
import org.L2.community.infrastructure.mapper.ForumPostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论领域服务。
 */
@Service
public class ForumCommentService {

    @Autowired
    private ForumCommentMapper forumCommentMapper;

    @Autowired
    private ForumPostMapper forumPostMapper;

    @AutoFill(OperationType.INSERT)
    @Transactional(rollbackFor = Exception.class)
    public R createComment(ForumComment comment) {
        if (comment == null
                || comment.getPostId() == null
                || comment.getUserId() == null
                || comment.getContent() == null
                || comment.getContent().isBlank()) {
            return R.error("评论信息不完整");
        }

        try {
            boolean isRootComment = (comment.getParentId() == null);

            if (isRootComment) {
                comment.setCommentType((byte) 1);
                Integer maxFloor = forumCommentMapper.getMaxFloorNo(comment.getPostId());
                comment.setFloorNo(maxFloor != null ? maxFloor + 1 : 1);
                comment.setReplyCount(0);
            } else {
                comment.setCommentType((byte) 2);
                ForumComment parentComment = forumCommentMapper.selectById(comment.getParentId());
                if (parentComment == null) {
                    return R.error("父评论不存在");
                }
                Long rootId = parentComment.getCommentType() == 1
                        ? parentComment.getId()
                        : parentComment.getRootId();
                comment.setRootId(rootId);
            }

            if (comment.getStatus() == null) {
                comment.setStatus((byte) 0);
            }

            forumCommentMapper.insert(comment);

            if (isRootComment) {
                comment.setRootId(comment.getId());
                forumCommentMapper.update(comment);
            } else {
                ForumComment rootComment = forumCommentMapper.selectById(comment.getRootId());
                if (rootComment != null) {
                    Integer replyCount = rootComment.getReplyCount();
                    rootComment.setReplyCount(replyCount != null ? replyCount + 1 : 1);
                    forumCommentMapper.update(rootComment);
                }
            }

            ForumPost post = forumPostMapper.selectById(comment.getPostId());
            if (post != null) {
                Integer count = post.getCommentCount();
                if (count == null) {
                    count = 0;
                }
                post.setCommentCount(count + 1);
                post.setLastCommentAt(LocalDateTime.now());
                forumPostMapper.update(post);
            }

            return R.success("评论成功");
        } catch (Exception e) {
            return R.error("评论失败" + e.getMessage());
        }
    }

    public List<ForumComment> queryComments(ForumComment condition) {
        return forumCommentMapper.query(condition);
    }

    @Transactional(rollbackFor = Exception.class)
    public R deleteComment(Long id) {
        if (id == null) {
            return R.error("评论ID不能为空");
        }
        try {
            ForumComment comment = forumCommentMapper.selectById(id);
            if (comment == null) {
                return R.error("评论不存在");
            }

            forumCommentMapper.deleteById(id);

            ForumPost post = forumPostMapper.selectById(comment.getPostId());
            if (post != null) {
                Integer count = post.getCommentCount();
                post.setCommentCount(count != null && count > 0 ? count - 1 : 0);
                forumPostMapper.update(post);
            }

            if (comment.getParentId() != null) {
                Long rootId = comment.getRootId();
                if (rootId != null) {
                    ForumComment rootComment = forumCommentMapper.selectById(rootId);
                    if (rootComment != null) {
                        Integer replyCount = rootComment.getReplyCount();
                        rootComment.setReplyCount(replyCount != null && replyCount > 0 ? replyCount - 1 : 0);
                        forumCommentMapper.update(rootComment);
                    }
                }
            }

            return R.success("评论删除成功");
        } catch (Exception e) {
            return R.error("评论删除失败" + e.getMessage());
        }
    }

    public void deleteByPostId(Long postId) {
        if (postId != null) {
            forumCommentMapper.deleteByPostId(postId);
        }
    }
}
