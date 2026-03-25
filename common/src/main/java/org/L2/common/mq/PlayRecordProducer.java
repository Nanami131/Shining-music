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
     * 发送播放开始事件到 MQ（兼容旧调用）
     */
    public void sendPlayRecord(Long userId, Long songId, String playSessionId) {
        PlaybackInfo info = new PlaybackInfo().setPlaySessionId(playSessionId);
        sendPlayEvent(userId, songId, EventType.EVENT_NAME_SONG_PLAY, info);
    }

    /**
     * 发送播放结束事件到 MQ（包含播放时长等增强字段）
     */
    public void sendPlayEndRecord(Long userId, Long songId, PlaybackInfo playbackInfo) {
        sendPlayEvent(userId, songId, EventType.EVENT_NAME_SONG_PLAY_END, playbackInfo);
    }

    private void sendPlayEvent(Long userId, Long songId, String eventName, PlaybackInfo extraInfo) {
        EventInfo eventInfo = new EventInfo()
                .setEventId(UUID.randomUUID().toString())
                .setEventCategory(EventType.EVENT_CATEGORY_PLAYBACK)
                .setEventName(eventName)
                .setOccurredAt(LocalDateTime.now())
                .setTraceId(UUID.randomUUID().toString());

        UserInfo userInfo = new UserInfo()
                .setUserId(userId);

        PlaybackInfo playbackInfo = (extraInfo != null) ? extraInfo : new PlaybackInfo();
        playbackInfo.setSongId(songId);

        PlaybackEventMessage message = new PlaybackEventMessage()
                .setEvent(eventInfo)
                .setUser(userInfo)
                .setPlayback(playbackInfo);

        try {
            log.info("Sending {} event to RabbitMQ, userId={}, songId={}",
                    eventName, userId, songId);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PLAY_RECORD_EXCHANGE,
                    RabbitMQConfig.PLAY_RECORD_ROUTING_KEY,
                    message
            );
        } catch (Exception e) {
            log.error("Failed to send {} event, userId={}, songId={}",
                    eventName, userId, songId, e);
            throw e;
        }
    }
}
