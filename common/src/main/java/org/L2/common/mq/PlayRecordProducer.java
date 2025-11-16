package org.L2.common.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.L2.common.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlayRecordProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendPlayRecord(Long userId) {
        PlayRecordMessage message = new PlayRecordMessage()
                .setUserId(userId);
        try {
            log.info("Sending play record message to RabbitMQ, userId={}", userId);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PLAY_RECORD_EXCHANGE,
                    RabbitMQConfig.PLAY_RECORD_ROUTING_KEY,
                    message
            );
            log.info("Play record message sent successfully, userId={}", userId);
        } catch (Exception e) {
            log.error("Failed to send play record message, userId={}", userId, e);
            throw e;
        }
    }
}

