package org.L2.common.rpc;

import org.L2.common.R;
import org.L2.common.rpc.fallback.StatisticsClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "statistics-service", fallbackFactory = StatisticsClientFallbackFactory.class)
public interface StatisticsClient {

    @GetMapping("/statistics/user/{userId}/plays/history")
    R getPlayHistory(@PathVariable("userId") Long userId, @RequestParam("limit") int limit);

    @GetMapping("/statistics/user/{userId}/plays/song-ids")
    R getPlayedSongIds(@PathVariable("userId") Long userId);

    @GetMapping("/statistics/user/ranking/top-songs")
    R getGlobalTopSongs(@RequestParam(value = "limit", defaultValue = "20") int limit);

    @GetMapping("/statistics/user/interactions/all")
    R getAllUserSongPlayCounts();

    @GetMapping("/statistics/user/{userId}/plays/song-counts")
    R getUserSongPlayCounts(@PathVariable("userId") Long userId);
}
