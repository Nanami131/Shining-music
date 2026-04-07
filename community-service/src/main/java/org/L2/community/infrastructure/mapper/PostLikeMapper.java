package org.L2.community.infrastructure.mapper;

import org.L2.community.domain.model.PostLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostLikeMapper {

    int insert(PostLike like);

    int delete(@Param("postId") Long postId, @Param("userId") Long userId);

    PostLike selectByPair(@Param("postId") Long postId, @Param("userId") Long userId);

    int countByPost(@Param("postId") Long postId);
}
