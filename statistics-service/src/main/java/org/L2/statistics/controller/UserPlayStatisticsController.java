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
     * 获取用户在指定时间范围内的听歌总次数
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
     * 获取用户在指定时间范围内，按天分组的听歌次数
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
