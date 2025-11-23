package org.L2.statistics.domain.service;

import lombok.RequiredArgsConstructor;
import org.L2.statistics.domain.model.UserDailyPlayCount;
import org.L2.statistics.domain.model.UserSongPlayRecord;
import org.L2.statistics.infrastructure.mapper.UserSongPlayRecordMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 用户听歌记录的领域服务，封装聚合根的核心行为。
 */
@Service
@RequiredArgsConstructor
public class UserPlayRecordDomainService {

    private final UserSongPlayRecordMapper userSongPlayRecordMapper;

    /**
     * 保存一条听歌记录。
     */
    public void saveRecord(Long userId, Long songId, LocalDateTime playedAt) {
        UserSongPlayRecord record = new UserSongPlayRecord()
                .setUserId(userId)
                .setSongId(songId)
                .setPlayedAt(playedAt);
        userSongPlayRecordMapper.insert(record);
    }

    /**
     * 统计时间范围内的总听歌次数。
     */
    public long countByUserAndRange(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        Long count = userSongPlayRecordMapper.countByUserAndTimeRange(userId, startTime, endTime);
        return count == null ? 0L : count;
    }

    /**
     * 获取时间范围内按天聚合的听歌次数。
     */
    public List<UserDailyPlayCount> listUserDailyStats(Long userId,
                                                       LocalDateTime startTime,
                                                       LocalDateTime endTime) {
        List<UserDailyPlayCount> list =
                userSongPlayRecordMapper.countByUserGroupByDate(userId, startTime, endTime);
        return list == null ? Collections.emptyList() : list;
    }
}
