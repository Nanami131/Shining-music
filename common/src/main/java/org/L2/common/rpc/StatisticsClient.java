package org.L2.common.rpc;

import org.L2.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "statistics-service")
public interface StatisticsClient {

    @GetMapping("/statistics/user/{userId}/plays/history")
    R getPlayHistory(@PathVariable("userId") Long userId, @RequestParam("limit") int limit);

    @GetMapping("/statistics/user/{userId}/plays/song-ids")
    R getPlayedSongIds(@PathVariable("userId") Long userId);
}
