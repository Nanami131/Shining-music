package org.L2.statistics.infrastructure.mapper;

import org.L2.statistics.domain.model.UserDailyPlayCount;
import org.L2.statistics.domain.model.UserSongPlayRecord;
import org.L2.statistics.domain.model.UserTopSong;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserSongPlayRecordMapper {

    void insert(UserSongPlayRecord record);

    Long countByUserAndTimeRange(@Param("userId") Long userId,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    List<UserDailyPlayCount> countByUserGroupByDate(@Param("userId") Long userId,
                                                    @Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end);

    List<UserTopSong> topSongsByUserAndRange(@Param("userId") Long userId,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end,
                                             @Param("limit") int limit);

    List<Map<String, Object>> topSingersByUser(@Param("userId") Long userId,
                                                @Param("limit") int limit);

    Map<String, Object> activeHourByUser(@Param("userId") Long userId);

    Double avgCompletionByUser(@Param("userId") Long userId);

    Long totalDurationByUser(@Param("userId") Long userId);

    LocalDateTime firstPlayTimeByUser(@Param("userId") Long userId);

    LocalDateTime lastPlayTimeByUser(@Param("userId") Long userId);

    List<Long> allDistinctUserIds();

    List<UserSongPlayRecord> recentPlaysByUser(@Param("userId") Long userId,
                                               @Param("limit") int limit);

    List<Map<String, Object>> globalTopSongs(@Param("limit") int limit);

    void updatePlayEndRecord(@Param("userId") Long userId,
                             @Param("songId") Long songId,
                             @Param("playSessionId") String playSessionId,
                             @Param("durationSec") Integer durationSec,
                             @Param("totalDuration") Integer totalDuration,
                             @Param("completed") Boolean completed,
                             @Param("source") String source);
}
