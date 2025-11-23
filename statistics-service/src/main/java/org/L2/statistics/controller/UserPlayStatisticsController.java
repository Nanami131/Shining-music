package org.L2.statistics.controller;

import org.L2.common.R;
import org.L2.statistics.application.service.UserPlayStatisticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 用户听歌统计对外接口。
 */
@RestController
@RequestMapping("/statistics/user")
public class UserPlayStatisticsController {

    private final UserPlayStatisticsService userPlayStatisticsService;

    public UserPlayStatisticsController(UserPlayStatisticsService userPlayStatisticsService) {
        this.userPlayStatisticsService = userPlayStatisticsService;
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
}
