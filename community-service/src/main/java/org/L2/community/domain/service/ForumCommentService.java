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

        // TODO: commentType / status / rootId / floorNo / replyCount 的规则由你决定，这里不做具体假设

        try {
            forumCommentMapper.insert(comment);

            // 同步更新帖子评论数和最后评论时间
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

    public R deleteComment(Long id) {
        if (id == null) {
            return R.error("评论ID不能为空");
        }
        try {
            forumCommentMapper.deleteById(id);
            // TODO: 同步回写帖子 comment_count / last_comment_at，避免随意减计数导致不一致
            return R.success("评论删除成功");
        } catch (Exception e) {
            return R.error("评论删除失败" + e.getMessage());
        }
    }
}
