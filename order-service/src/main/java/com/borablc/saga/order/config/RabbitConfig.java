package com.borablc.saga.order.config;

import com.borablc.saga.common.Queues;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    @Bean
    public TopicExchange commandExchange(){
        return new TopicExchange(Queues.COMMAND_EXCHANGE);
    }
}
