package org.L2.recommend.infrastructure;

import org.L2.recommend.domain.model.UserPreference;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserPreferenceMapper {

    UserPreference selectByUserId(Long userId);

    void insertOrUpdate(UserPreference preference);
}
