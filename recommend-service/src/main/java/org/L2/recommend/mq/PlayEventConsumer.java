package org.L2.recommend.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.L2.common.constant.EventType;
import org.L2.common.context.RequestTraceContext;
import org.L2.common.event.PlaybackEventMessage;
import org.L2.common.mq.config.RabbitMQConfig;
import org.L2.recommend.service.UserPreferenceService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 消费播放结束事件，更新用户偏好向量。
 *
 * 单曲循环衰减（基于 Galak et al. 2013 hedonic adaptation）：
 * 同一用户对同一首歌的重复播放，偏好更新权重按 1/(1+ln(n)) 递减，
 * 防止偏好向量被单曲"焊死"。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlayEventConsumer {

    private final UserPreferenceService userPreferenceService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final double RATIO_SKIP_THRESHOLD = 0.1;
    private static final double RATIO_FULL_THRESHOLD = 0.8;
    private static final String SONG_PLAY_COUNT_PREFIX = "user:songplay:";

    @RabbitListener(queues = RabbitMQConfig.RECOMMEND_UPDATE_QUEUE)
    public void onPlayEvent(PlaybackEventMessage message) {
        String requestId = RequestTraceContext.resolveOrCreate(
                message != null && message.getEvent() != null ? message.getEvent().getTraceId() : null);
        RequestTraceContext.bind(requestId);
        try {
            if (message == null || message.getEvent() == null || message.getUser() == null || message.getPlayback() == null) {
                log.warn("Received malformed play event message, requestId={}, ignoring", requestId);
                return;
            }

            String eventName = message.getEvent().getEventName();
            if (!EventType.EVENT_NAME_SONG_PLAY_END.equals(eventName)) {
                return;
            }

            Long userId = message.getUser().getUserId();
            Long songId = message.getPlayback().getSongId();
            if (userId == null || songId == null) {
                log.warn("Missing userId or songId in play event, requestId={}, ignoring", requestId);
                return;
            }

            double baseWeight = calculateWeight(message.getPlayback());
            if (baseWeight <= 0) {
                log.debug("Skipping preference update for requestId={}, userId={}, songId={} (weight=0, likely skipped)",
                        requestId, userId, songId);
                return;
            }

            long playCount = incrementSongPlayCount(userId, songId);
            double decayMultiplier = 1.0 / (1.0 + Math.log(playCount));
            double effectiveWeight = baseWeight * decayMultiplier;

            try {
                boolean updated = userPreferenceService.incrementalUpdate(userId, songId, effectiveWeight);
                if (updated) {
                    log.info("Preference updated: requestId={}, userId={}, songId={}, baseWeight={}, playCount={}, decay={}, effective={}",
                            requestId, userId, songId,
                            String.format("%.2f", baseWeight),
                            playCount,
                            String.format("%.3f", decayMultiplier),
                            String.format("%.3f", effectiveWeight));
                } else {
                    log.warn("Preference update skipped: requestId={}, userId={}, songId={} (song vector missing)",
                            requestId, userId, songId);
                }
            } catch (Exception e) {
                log.error("Failed to update preference for requestId={}, userId={}, songId={}",
                        requestId, userId, songId, e);
                throw e;
            }
        } finally {
            RequestTraceContext.clear();
        }
    }

    private long incrementSongPlayCount(Long userId, Long songId) {
        String key = SONG_PLAY_COUNT_PREFIX + userId + ":" + songId;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        return count != null ? count : 1;
    }

    private double calculateWeight(org.L2.common.event.PlaybackInfo playback) {
        Integer totalDuration = playback.getTotalDurationSec();
        if (totalDuration == null || totalDuration <= 0) {
            return playback.getCompleted() != null && playback.getCompleted() ? 1.0 : 0.3;
        }

        int listenedTime = playback.getActualListenedTime() != null
                ? playback.getActualListenedTime()
                : (playback.getDurationSec() != null ? playback.getDurationSec() : 0);

        double ratio = (double) listenedTime / totalDuration;

        if (ratio < RATIO_SKIP_THRESHOLD) {
            return 0;
        } else if (ratio >= RATIO_FULL_THRESHOLD) {
            return 1.0;
        } else {
            return (ratio - RATIO_SKIP_THRESHOLD) / (RATIO_FULL_THRESHOLD - RATIO_SKIP_THRESHOLD);
        }
    }
}
