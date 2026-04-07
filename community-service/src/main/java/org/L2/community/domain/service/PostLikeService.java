package org.L2.community.domain.service;

import org.L2.community.domain.model.ForumPost;
import org.L2.community.domain.model.PostLike;
import org.L2.community.infrastructure.mapper.ForumPostMapper;
import org.L2.community.infrastructure.mapper.PostLikeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostLikeService {

    @Autowired
    private PostLikeMapper postLikeMapper;

    @Autowired
    private ForumPostMapper forumPostMapper;

    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(Long postId, Long userId) {
        PostLike existing = postLikeMapper.selectByPair(postId, userId);
        boolean liked;
        if (existing != null) {
            postLikeMapper.delete(postId, userId);
            liked = false;
        } else {
            PostLike like = new PostLike().setPostId(postId).setUserId(userId);
            postLikeMapper.insert(like);
            liked = true;
        }
        int count = postLikeMapper.countByPost(postId);
        forumPostMapper.update(new ForumPost().setId(postId).setLikeCount(count));
        return liked;
    }

    public boolean isLiked(Long postId, Long userId) {
        return postLikeMapper.selectByPair(postId, userId) != null;
    }
}
