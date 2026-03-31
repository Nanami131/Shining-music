package org.L2.statistics.domain.service;

import lombok.RequiredArgsConstructor;
import org.L2.statistics.domain.model.UserDailyPlayCount;
import org.L2.statistics.domain.model.UserSongPlayRecord;
import org.L2.statistics.domain.model.UserTopSong;
import org.L2.statistics.infrastructure.mapper.UserSongPlayRecordMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserPlayRecordDomainService {

    private final UserSongPlayRecordMapper userSongPlayRecordMapper;

    public void saveRecord(Long userId, Long songId, LocalDateTime playedAt) {
        UserSongPlayRecord record = new UserSongPlayRecord()
                .setUserId(userId)
                .setSongId(songId)
                .setPlayedAt(playedAt);
        userSongPlayRecordMapper.insert(record);
    }

    public void saveFullRecord(UserSongPlayRecord record) {
        userSongPlayRecordMapper.insert(record);
    }

    public void updatePlayEndRecord(Long userId, Long songId, String playSessionId,
                                     Integer durationSec, Integer totalDuration,
                                     Boolean completed, String source) {
        userSongPlayRecordMapper.updatePlayEndRecord(userId, songId, playSessionId,
                durationSec, totalDuration, completed, source);
    }

    public long countByUserAndRange(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        Long count = userSongPlayRecordMapper.countByUserAndTimeRange(userId, startTime, endTime);
        return count == null ? 0L : count;
    }

    public List<UserDailyPlayCount> listUserDailyStats(Long userId,
                                                       LocalDateTime startTime,
                                                       LocalDateTime endTime) {
        List<UserDailyPlayCount> list =
                userSongPlayRecordMapper.countByUserGroupByDate(userId, startTime, endTime);
        return list == null ? Collections.emptyList() : list;
    }

    public List<UserTopSong> listTopSongsByUser(Long userId,
                                                LocalDateTime startTime,
                                                LocalDateTime endTime,
                                                int limit) {
        List<UserTopSong> songs =
                userSongPlayRecordMapper.topSongsByUserAndRange(userId, startTime, endTime, limit);
        return songs == null ? Collections.emptyList() : songs;
    }

    public List<Map<String, Object>> listTopSingersByUser(Long userId, int limit) {
        return userSongPlayRecordMapper.topSingersByUser(userId, limit);
    }

    public Map<String, Object> getActiveHour(Long userId) {
        return userSongPlayRecordMapper.activeHourByUser(userId);
    }

    public Double getAvgCompletion(Long userId) {
        return userSongPlayRecordMapper.avgCompletionByUser(userId);
    }

    public Long getTotalDuration(Long userId) {
        return userSongPlayRecordMapper.totalDurationByUser(userId);
    }

    public LocalDateTime getFirstPlayTime(Long userId) {
        return userSongPlayRecordMapper.firstPlayTimeByUser(userId);
    }

    public LocalDateTime getLastPlayTime(Long userId) {
        return userSongPlayRecordMapper.lastPlayTimeByUser(userId);
    }

    public List<Long> getAllDistinctUserIds() {
        return userSongPlayRecordMapper.allDistinctUserIds();
    }

    public List<UserSongPlayRecord> recentPlaysByUser(Long userId, int limit) {
        return userSongPlayRecordMapper.recentPlaysByUser(userId, limit);
    }

    public List<java.util.Map<String, Object>> globalTopSongs(int limit) {
        return userSongPlayRecordMapper.globalTopSongs(limit);
    }

    public List<Long> distinctSongIdsByUser(Long userId) {
        return userSongPlayRecordMapper.distinctSongIdsByUser(userId);
    }
}
