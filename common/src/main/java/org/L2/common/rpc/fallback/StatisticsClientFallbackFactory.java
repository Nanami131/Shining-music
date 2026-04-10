package org.L2.common.rpc.fallback;

import lombok.extern.slf4j.Slf4j;
import org.L2.common.R;
import org.L2.common.rpc.StatisticsClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StatisticsClientFallbackFactory implements FallbackFactory<StatisticsClient> {

    @Override
    public StatisticsClient create(Throwable cause) {
        log.warn("StatisticsClient fallback triggered: {}", cause.getMessage());
        return new StatisticsClient() {
            @Override
            public R getPlayHistory(Long userId, int limit) {
                return R.error("统计服务暂时不可用");
            }

            @Override
            public R getPlayedSongIds(Long userId) {
                return R.error("统计服务暂时不可用");
            }

            @Override
            public R getGlobalTopSongs(int limit) {
                return R.error("统计服务暂时不可用");
            }

            @Override
            public R getAllUserSongPlayCounts() {
                return R.error("统计服务暂时不可用");
            }

            @Override
            public R getUserSongPlayCounts(Long userId) {
                return R.error("统计服务暂时不可用");
            }
        };
    }
}
