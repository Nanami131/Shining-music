package org.L2.statistics.infrastructure.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.L2.common.event.PlaybackEventMessage;
import org.L2.common.mq.config.RabbitMQConfig;
import org.L2.statistics.application.service.UserPlayStatisticsService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 播放事件消费者。
 * 从 MQ 中消费 PlaybackEventMessage，并落库存为听歌记录。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlayRecordConsumer {

    private final UserPlayStatisticsService userPlayStatisticsService;

    /**
     * 消费播放事件消息。
     *
     * @param message MQ 中的播放事件
     */
    @RabbitListener(queues = RabbitMQConfig.PLAY_RECORD_QUEUE)
    public void onMessage(PlaybackEventMessage message) {
        try {
            log.info("Received playback event from MQ: {}", message);
            userPlayStatisticsService.saveFromEvent(message);
        } catch (Exception e) {
            log.error("Handle playback event failed, message={}", message, e);
            throw e;
        }
    }
}
