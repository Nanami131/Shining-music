package org.L2.community.infrastructure.mapper;

import org.L2.community.domain.model.ForumComment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 帖子评论持久层接口，对应 post_comments 表。
 */
@Mapper
public interface ForumCommentMapper {

    ForumComment selectById(Long id);

    void insert(ForumComment comment);

    void update(ForumComment comment);

    void deleteById(Long id);

    List<ForumComment> query(ForumComment condition);
}
