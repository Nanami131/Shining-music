package org.L2.common.mq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String PLAY_RECORD_EXCHANGE = "play.record.exchange";
    public static final String PLAY_RECORD_QUEUE = "play.record.queue";
    public static final String PLAY_RECORD_ROUTING_KEY = "play.record";

    public static final String RECOMMEND_UPDATE_QUEUE = "recommend.update.queue";

    @Bean
    public TopicExchange playRecordExchange() {
        return new TopicExchange(PLAY_RECORD_EXCHANGE, true, false);
    }

    @Bean
    public Queue playRecordQueue() {
        return new Queue(PLAY_RECORD_QUEUE, true);
    }

    @Bean
    public Binding playRecordBinding(@Qualifier("playRecordQueue") Queue playRecordQueue,
                                     TopicExchange playRecordExchange) {
        return BindingBuilder.bind(playRecordQueue)
                .to(playRecordExchange)
                .with(PLAY_RECORD_ROUTING_KEY);
    }

    @Bean
    public Queue recommendUpdateQueue() {
        return new Queue(RECOMMEND_UPDATE_QUEUE, true);
    }

    @Bean
    public Binding recommendUpdateBinding(@Qualifier("recommendUpdateQueue") Queue recommendUpdateQueue,
                                          TopicExchange playRecordExchange) {
        return BindingBuilder.bind(recommendUpdateQueue)
                .to(playRecordExchange)
                .with(PLAY_RECORD_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}

