package com.borablc.saga.inventory.config;

import com.borablc.saga.common.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    @Bean
    public NewTopic sagaRepliesTopic(){
        return TopicBuilder
                .name(Topics.SAGA_REPLIES)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
