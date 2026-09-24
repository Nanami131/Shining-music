package org.L2.statistics.controller;

import org.L2.common.R;
import org.L2.statistics.application.service.AnnualReportService;
import org.L2.statistics.application.service.UserPlayStatisticsService;
import org.L2.statistics.application.service.UserProfileService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.Year;

@RestController
@RequestMapping("/statistics/user")
public class UserPlayStatisticsController {

    private final UserPlayStatisticsService userPlayStatisticsService;
    private final UserProfileService userProfileService;
    private final AnnualReportService annualReportService;

    public UserPlayStatisticsController(UserPlayStatisticsService userPlayStatisticsService,
                                         UserProfileService userProfileService,
                                         AnnualReportService annualReportService) {
        this.userPlayStatisticsService = userPlayStatisticsService;
        this.userProfileService = userProfileService;
        this.annualReportService = annualReportService;
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
    public R getUserTopSongs(Long userId, String dimension, Integer limit) {
        return getUserTopSongs(userId, dimension, limit, null, null);
    }

    @GetMapping("/{userId}/plays/top-songs")
    public R getUserTopSongs(@PathVariable("userId") Long userId,
                             @RequestParam(value = "dimension", required = false) String dimension,
                             @RequestParam(value = "limit", required = false) Integer limit,
                             @RequestParam(value = "page", required = false) Integer page,
                             @RequestParam(value = "size", required = false) Integer size) {
        if (page != null && size != null && page > 0 && size > 0) {
            R result = userPlayStatisticsService.getUserTopSongsPage(userId, dimension,
                    ((long) page - 1L) * size, size);
            if (!Boolean.TRUE.equals(result.getPassed())) return result;
            return org.L2.common.ListPagination.fromPage(result,
                    userPlayStatisticsService.countUserTopSongs(userId, dimension), page, size);
        }
        if (page != null || size != null) {
            if (page == null || size == null || page != 1 || size != 0) return R.error("分页参数无效");
            return org.L2.common.ListPagination.apply(
                    userPlayStatisticsService.getUserTopSongs(userId, dimension, 0), page, size);
        }
        return userPlayStatisticsService.getUserTopSongs(userId, dimension, limit);
    }

    public R getUserTopSingers(Long userId, int limit) {
        return getUserTopSingers(userId, limit, null, null);
    }

    @GetMapping("/{userId}/plays/top-singers")
    public R getUserTopSingers(@PathVariable("userId") Long userId,
                               @RequestParam(value = "limit", defaultValue = "5") int limit,
                               @RequestParam(value = "page", required = false) Integer page,
                               @RequestParam(value = "size", required = false) Integer size) {
        if (page != null && size != null && page > 0 && size > 0) {
            R result = userPlayStatisticsService.getUserTopSingersPage(userId,
                    ((long) page - 1L) * size, size);
            if (!Boolean.TRUE.equals(result.getPassed())) return result;
            return org.L2.common.ListPagination.fromPage(result,
                    userPlayStatisticsService.countUserTopSingers(userId), page, size);
        }
        if (page != null || size != null) {
            if (page == null || size == null || page != 1 || size != 0) return R.error("分页参数无效");
            return org.L2.common.ListPagination.apply(
                    userPlayStatisticsService.getUserTopSingers(userId, 0), page, size);
        }
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

    public R getRecentPlays(Long userId, int limit) { return getRecentPlays(userId, limit, null, null); }

    @GetMapping("/{userId}/plays/history")
    public R getRecentPlays(@PathVariable("userId") Long userId,
                            @RequestParam(value = "limit", defaultValue = "30") int limit,
                            @RequestParam(value = "page", required = false) Integer page,
                            @RequestParam(value = "size", required = false) Integer size) {
        if (page != null && size != null && page > 0 && size > 0) {
            if (userId == null) return R.error("用户ID不能为空");
            R result = userPlayStatisticsService.getRecentPlaysPage(userId, ((long) page - 1L) * size, size);
            return org.L2.common.ListPagination.fromPage(result,
                    userPlayStatisticsService.countRecentPlays(userId), page, size);
        }
        return org.L2.common.ListPagination.apply(userPlayStatisticsService.getRecentPlays(userId,
                page != null && size != null ? 0 : limit), page, size);
    }

    @GetMapping("/{userId}/plays/song-ids")
    public R getPlayedSongIds(@PathVariable("userId") Long userId) {
        return userPlayStatisticsService.getDistinctPlayedSongIds(userId);
    }

    public R getGlobalTopSongs(int limit) { return getGlobalTopSongs(limit, null, null); }

    @GetMapping("/ranking/top-songs")
    public R getGlobalTopSongs(@RequestParam(value = "limit", defaultValue = "20") int limit,
                               @RequestParam(value = "page", required = false) Integer page,
                               @RequestParam(value = "size", required = false) Integer size) {
        if (page != null && size != null && page > 0 && size > 0) {
            R result = userPlayStatisticsService.getGlobalTopSongsPage(((long) page - 1L) * size, size);
            return org.L2.common.ListPagination.fromPage(result,
                    userPlayStatisticsService.countGlobalTopSongs(), page, size);
        }
        return org.L2.common.ListPagination.apply(userPlayStatisticsService.getGlobalTopSongs(
                page != null && size != null ? 0 : limit), page, size);
    }

    @GetMapping("/interactions/all")
    public R getAllUserSongPlayCounts() {
        return userPlayStatisticsService.getAllUserSongPlayCounts();
    }

    @GetMapping("/{userId}/plays/song-counts")
    public R getUserSongPlayCounts(@PathVariable("userId") Long userId) {
        return userPlayStatisticsService.getUserSongPlayCounts(userId);
    }

    @GetMapping("/{userId}/annual-report")
    public R getAnnualReport(@PathVariable("userId") Long userId,
                             @RequestParam(value = "year", required = false) Integer year) {
        int reportYear = (year != null) ? year : Year.now().getValue();
        return annualReportService.generateAnnualReport(userId, reportYear);
    }
}
