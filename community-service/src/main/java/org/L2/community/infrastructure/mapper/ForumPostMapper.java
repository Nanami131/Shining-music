package org.L2.community.infrastructure.mapper;

import org.L2.community.domain.model.ForumPost;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 帖子持久层接口，对应 posts 表。
 */
@Mapper
public interface ForumPostMapper {

    ForumPost selectById(Long id);

    void insert(ForumPost post);

    void update(ForumPost post);

    void deleteById(Long id);

    List<ForumPost> query(ForumPost condition);
}
