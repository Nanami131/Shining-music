package org.L2.community.domain.service;

import org.L2.common.R;
import org.L2.common.annotation.AutoFill;
import org.L2.common.constant.OperationType;
import org.L2.community.domain.model.ForumPost;
import org.L2.community.infrastructure.mapper.ForumPostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 帖子领域服务。
 */
@Service
public class ForumPostService {

    @Autowired
    private ForumPostMapper forumPostMapper;

    @AutoFill(OperationType.INSERT)
    public R createPost(ForumPost post) {
        if (post == null
                || post.getUserId() == null
                || post.getTitle() == null
                || post.getTitle().isBlank()
                || post.getContent() == null
                || post.getContent().isBlank()) {
            return R.error("帖子信息不完整");
        }

        // TODO: 根据业务为 status 赋值（例如：1-正常），这里不做假设
        try {
            forumPostMapper.insert(post);
            return R.success("发帖成功", post.getId());
        } catch (Exception e) {
            return R.error("发帖失败" + e.getMessage());
        }
    }

    @AutoFill(OperationType.UPDATE)
    public R updatePost(ForumPost post) {
        if (post == null || post.getId() == null) {
            return R.error("帖子ID不能为空");
        }
        try {
            forumPostMapper.update(post);
            return R.success("帖子更新成功");
        } catch (Exception e) {
            return R.error("帖子更新失败" + e.getMessage());
        }
    }

    public R deletePost(Long id) {
        if (id == null) {
            return R.error("帖子ID不能为空");
        }
        try {
            forumPostMapper.deleteById(id);
            return R.success("帖子删除成功");
        } catch (Exception e) {
            return R.error("帖子删除失败" + e.getMessage());
        }
    }

    public ForumPost getPostById(Long id) {
        if (id == null) {
            return null;
        }
        return forumPostMapper.selectById(id);
    }

    public List<ForumPost> queryPosts(ForumPost condition) {
        return forumPostMapper.query(condition);
    }
}
