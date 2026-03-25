package org.L2.statistics.infrastructure.mapper;

import org.L2.statistics.domain.model.UserEventLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserEventLogMapper {

    void insert(UserEventLog record);

    List<UserEventLog> selectByUser(@Param("userId") Long userId,
                                     @Param("eventType") String eventType,
                                     @Param("limit") int limit);

    List<Map<String, Object>> topSearchKeywords(@Param("userId") Long userId,
                                                 @Param("limit") int limit);
}
