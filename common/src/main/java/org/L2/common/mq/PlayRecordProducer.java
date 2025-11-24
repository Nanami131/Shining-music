package org.L2.common.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.L2.common.constant.EventType;
import org.L2.common.event.EventInfo;
import org.L2.common.event.PlaybackEventMessage;
import org.L2.common.event.PlaybackInfo;
import org.L2.common.event.UserInfo;
import org.L2.common.mq.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;



@Component
@RequiredArgsConstructor
@Slf4j
public class PlayRecordProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送播放事件到 MQ
     */
    public void sendPlayRecord(Long userId, Long songId) {
        // 组装事件元信息
        EventInfo eventInfo = new EventInfo()
                .setEventId(UUID.randomUUID().toString()) // todo
                .setEventCategory(EventType.EVENT_CATEGORY_PLAYBACK)
                .setEventName(EventType.EVENT_NAME_SONG_PLAY)
                .setOccurredAt(LocalDateTime.now())
                .setTraceId(UUID.randomUUID().toString()); // todo

        UserInfo userInfo = new UserInfo()
                .setUserId(userId);

        PlaybackInfo playbackInfo = new PlaybackInfo()
                .setSongId(songId);

        // 顶层消息
        PlaybackEventMessage message = new PlaybackEventMessage()
                .setEvent(eventInfo)
                .setUser(userInfo)
                .setPlayback(playbackInfo);

        try {
            log.info("Sending playback event to RabbitMQ, userId={}, songId={}, eventId={}",
                    userId, songId, eventInfo.getEventId());
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PLAY_RECORD_EXCHANGE,
                    RabbitMQConfig.PLAY_RECORD_ROUTING_KEY,
                    message
            );
            log.info("Playback event sent successfully, userId={}, songId={}, eventId={}",
                    userId, songId, eventInfo.getEventId());
        } catch (Exception e) {
            log.error("Failed to send playback event, userId={}, songId={}, eventId={}",
                    userId, songId, eventInfo.getEventId(), e);
            throw e;
        }
    }
}
