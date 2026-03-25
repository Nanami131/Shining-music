package org.L2.statistics.infrastructure.mapper;

import org.L2.statistics.domain.model.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserProfileMapper {

    UserProfile selectByUserId(@Param("userId") Long userId);

    void insertOrUpdate(UserProfile profile);
}
