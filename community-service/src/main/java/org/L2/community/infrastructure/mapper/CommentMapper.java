package org.L2.community.infrastructure.mapper;

import org.L2.community.domain.model.Comment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CommentMapper {
    Comment selectById(Long id);

    void insert(Comment comment);

    void update(Comment comment);

    void deleteById(Long id);

    List<Comment> query(Comment comment);
}