package com.borablc.saga.order.config;

import com.borablc.saga.common.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    @Bean
    public NewTopic orderEventsTopic(){
        return TopicBuilder
                .name(Topics.ORDER_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
