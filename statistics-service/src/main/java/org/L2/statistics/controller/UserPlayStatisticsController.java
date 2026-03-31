package org.L2.statistics.controller;

import org.L2.common.R;
import org.L2.statistics.application.service.UserPlayStatisticsService;
import org.L2.statistics.application.service.UserProfileService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/statistics/user")
public class UserPlayStatisticsController {

    private final UserPlayStatisticsService userPlayStatisticsService;
    private final UserProfileService userProfileService;

    public UserPlayStatisticsController(UserPlayStatisticsService userPlayStatisticsService,
                                         UserProfileService userProfileService) {
        this.userPlayStatisticsService = userPlayStatisticsService;
        this.userProfileService = userProfileService;
    }

    /**
     * 获取用户在指定时间范围内的听歌总次数。
     *
     * @param userId    用户 ID
     * @param startTime 统计起始时间，可为空
     * @param endTime   统计结束时间，可为空
     * @return 统一返回体
     */
    @GetMapping("/{userId}/plays/count")
    public R getUserPlayCount(@PathVariable("userId") Long userId,
                              @RequestParam(value = "startTime", required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                              @RequestParam(value = "endTime", required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return userPlayStatisticsService.getUserPlayCount(userId, startTime, endTime);
    }

    /**
     * 获取用户在指定时间范围内按天分组的听歌次数。
     *
     * @param userId    用户 ID
     * @param startTime 统计起始时间，可为空
     * @param endTime   统计结束时间，可为空
     * @return 每日听歌次数列表
     */
    @GetMapping("/{userId}/plays/daily")
    public R getUserDailyPlayStats(@PathVariable("userId") Long userId,
                                   @RequestParam(value = "startTime", required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                   @RequestParam(value = "endTime", required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return userPlayStatisticsService.getUserDailyPlayStats(userId, startTime, endTime);
    }

    /**
     * 获取某位用户播放次数最多的歌曲。
     *
     * @param userId    用户 ID
     * @param dimension 时间维度
     * @param limit     返回条数
     * @return 歌曲播放统计
     */
    @GetMapping("/{userId}/plays/top-songs")
    public R getUserTopSongs(@PathVariable("userId") Long userId,
                             @RequestParam(value = "dimension", required = false) String dimension,
                             @RequestParam(value = "limit", required = false) Integer limit) {
        return userPlayStatisticsService.getUserTopSongs(userId, dimension, limit);
    }

    @GetMapping("/{userId}/plays/top-singers")
    public R getUserTopSingers(@PathVariable("userId") Long userId,
                               @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return userPlayStatisticsService.getUserTopSingers(userId, limit);
    }

    @GetMapping("/{userId}/profile")
    public R getUserProfile(@PathVariable("userId") Long userId) {
        return userProfileService.getUserProfile(userId);
    }

    @PostMapping("/{userId}/profile/refresh")
    public R refreshUserProfile(@PathVariable("userId") Long userId) {
        return userProfileService.refreshUserProfile(userId);
    }

    @PostMapping("/profiles/refresh-all")
    public R refreshAllProfiles() {
        return userProfileService.refreshAllProfiles();
    }

    @GetMapping("/{userId}/plays/history")
    public R getRecentPlays(@PathVariable("userId") Long userId,
                            @RequestParam(value = "limit", defaultValue = "30") int limit) {
        return userPlayStatisticsService.getRecentPlays(userId, limit);
    }

    @GetMapping("/{userId}/plays/song-ids")
    public R getPlayedSongIds(@PathVariable("userId") Long userId) {
        return userPlayStatisticsService.getDistinctPlayedSongIds(userId);
    }

    @GetMapping("/ranking/top-songs")
    public R getGlobalTopSongs(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        return userPlayStatisticsService.getGlobalTopSongs(limit);
    }
}
