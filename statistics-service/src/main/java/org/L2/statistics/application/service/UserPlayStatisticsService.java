package org.L2.statistics.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.L2.common.R;
import org.L2.common.event.PlaybackEventMessage;
import org.L2.statistics.application.dto.UserPlayCountByDateDTO;
import org.L2.statistics.domain.model.UserSongPlayRecord;
import org.L2.statistics.infrastructure.mapper.UserSongPlayRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 用户听歌统计应用服务。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserPlayStatisticsService {

    private final UserSongPlayRecordMapper userSongPlayRecordMapper;

    /**
     * 根据播放事件保存一条听歌记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveFromEvent(PlaybackEventMessage message) {
        if (message == null || message.getUser() == null || message.getPlayback() == null) {
            log.warn("PlaybackEventMessage is incomplete, ignore. message={}", message);
            return;
        }
        Long userId = message.getUser().getUserId();
        Long songId = message.getPlayback().getSongId();
        if (userId == null || songId == null) {
            log.warn("PlaybackEventMessage missing userId or songId, ignore. userId={}, songId={}", userId, songId);
            return;
        }

        LocalDateTime playedAt = null;
        if (message.getEvent() != null) {
            playedAt = message.getEvent().getOccurredAt();
        }
        if (playedAt == null) {
            playedAt = LocalDateTime.now();
        }

        UserSongPlayRecord record = new UserSongPlayRecord()
                .setUserId(userId)
                .setSongId(songId)
                .setPlayedAt(playedAt);

        userSongPlayRecordMapper.insert(record);
    }

    /**
     * 统计某用户在指定时间范围内的听歌次数。
     *
     * startTime / endTime 都可以为 null：
     *  - startTime 为空：不限制起始时间
     *  - endTime 为空：不限制结束时间
     */
    public R getUserPlayCount(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        if (userId == null) {
            return R.error("用户ID不能为空");
        }

        Long count = userSongPlayRecordMapper.countByUserAndTimeRange(userId, startTime, endTime);
        long safeCount = count != null ? count : 0L;
        return R.success("获取用户听歌次数成功", safeCount);
    }

    /**
     * 统计某用户在指定时间范围内，按天分组的听歌次数。
     *
     * 时间范围仍然是任意的，只是最终用 DATE(played_at) 做聚合。
     */
    public R getUserDailyPlayStats(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        if (userId == null) {
            return R.error("用户ID不能为空");
        }

        List<UserPlayCountByDateDTO> list =
                userSongPlayRecordMapper.countByUserGroupByDate(userId, startTime, endTime);
        if (list == null) {
            list = Collections.emptyList();
        }
        return R.success("获取用户按天听歌次数成功", list);
    }
}
