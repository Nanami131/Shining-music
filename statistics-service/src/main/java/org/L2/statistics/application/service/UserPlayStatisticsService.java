package org.L2.statistics.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.L2.common.R;
import org.L2.common.event.PlaybackEventMessage;
import org.L2.statistics.application.dto.UserPlayCountByDateDTO;
import org.L2.statistics.application.dto.UserTopSongDTO;
import org.L2.statistics.application.enums.PlayStatDimension;
import org.L2.statistics.domain.model.UserDailyPlayCount;
import org.L2.statistics.domain.model.UserTopSong;
import org.L2.statistics.domain.service.UserPlayRecordDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户听歌统计应用服务。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserPlayStatisticsService {

    private static final int DEFAULT_TOP_LIMIT = 10;
    private static final int MAX_TOP_LIMIT = 20;

    private final UserPlayRecordDomainService userPlayRecordDomainService;

    /**
     * 根据播放事件保存一条听歌记录。
     *
     * @param message 播放事件消息，内部包含用户与歌曲信息
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

        userPlayRecordDomainService.saveRecord(userId, songId, playedAt);
    }

    /**
     * 统计某用户在指定时间范围内的听歌次数。
     *
     * @param userId    用户 ID，不能为空
     * @param startTime 起始时间，可为空表示不限制
     * @param endTime   结束时间，可为空表示不限制
     * @return 包含统计结果的统一返回对象
     */
    public R getUserPlayCount(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        if (userId == null) {
            return R.error("用户ID不能为空");
        }

        long count = userPlayRecordDomainService.countByUserAndRange(userId, startTime, endTime);
        return R.success("获取用户听歌次数成功", count);
    }

    /**
     * 统计某用户在指定时间范围内，按天分组的听歌次数。
     *
     * @param userId    用户 ID，不能为空
     * @param startTime 起始时间，可为空表示不限制
     * @param endTime   结束时间，可为空表示不限制
     * @return 每日听歌次数 DTO 列表
     */
    public R getUserDailyPlayStats(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        if (userId == null) {
            return R.error("用户ID不能为空");
        }

        List<UserDailyPlayCount> stats =
                userPlayRecordDomainService.listUserDailyStats(userId, startTime, endTime);
        List<UserPlayCountByDateDTO> dtoList = stats.stream()
                .map(stat -> new UserPlayCountByDateDTO()
                        .setStatDate(stat.getStatDate())
                        .setPlayCount(stat.getPlayCount()))
                .collect(Collectors.toList());
        return R.success("获取用户按天听歌次数成功", dtoList);
    }

    /**
     * 获取某位用户在指定时间范围内最常播放的歌曲。
     *
     * @param userId    用户 ID
     * @param dimension 时间维度（TODAY/WEEK/MONTH/TOTAL）
     * @param limit     返回条数
     * @return 歌曲播放次数列表
     */
    public R getUserTopSongs(Long userId, String dimension, Integer limit) {
        if (userId == null) {
            return R.error("用户ID不能为空");
        }
        PlayStatDimension statDimension = PlayStatDimension.from(dimension);
        int size = (limit == null || limit <= 0) ? DEFAULT_TOP_LIMIT : Math.min(limit, MAX_TOP_LIMIT);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = statDimension.resolveStart(now);
        LocalDateTime endTime = statDimension.resolveEnd(now);

        List<UserTopSong> songs =
                userPlayRecordDomainService.listTopSongsByUser(userId, startTime, endTime, size);
        List<UserTopSongDTO> dtoList = songs.stream()
                .map(song -> new UserTopSongDTO()
                        .setSongId(song.getSongId())
                        .setPlayCount(song.getPlayCount()))
                .collect(Collectors.toList());
        return R.success("获取用户常听歌曲成功", dtoList);
    }
}
