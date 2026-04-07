package org.L2.community.infrastructure.mapper;

import org.L2.community.domain.model.UserFollow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserFollowMapper {

    int insert(UserFollow follow);

    int delete(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

    List<UserFollow> selectFollowing(@Param("userId") Long userId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    List<UserFollow> selectFollowers(@Param("userId") Long userId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    int countFollowing(@Param("userId") Long userId);

    int countFollowers(@Param("userId") Long userId);

    UserFollow selectByPair(@Param("followerId") Long followerId,
                            @Param("followingId") Long followingId);
}
