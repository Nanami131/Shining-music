package org.L2.community.infrastructure.mapper;

import org.L2.community.domain.model.Post;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PostMapper {
    Post selectById(Long id);

    void insert(Post post);

    void update(Post post);

    void deleteById(Long id);

    List<Post> query(Post post);
}