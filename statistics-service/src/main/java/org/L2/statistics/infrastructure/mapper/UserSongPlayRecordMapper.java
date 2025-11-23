package org.L2.statistics.infrastructure.mapper;

import org.L2.statistics.application.dto.UserPlayCountByDateDTO;
import org.L2.statistics.domain.model.UserSongPlayRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户听歌记录 Mapper。
 */
@Mapper
public interface UserSongPlayRecordMapper {

    void insert(UserSongPlayRecord record);


    Long countByUserAndTimeRange(@Param("userId") Long userId,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);


    List<UserPlayCountByDateDTO> countByUserGroupByDate(@Param("userId") Long userId,
                                                        @Param("start") LocalDateTime start,
                                                        @Param("end") LocalDateTime end);
}
