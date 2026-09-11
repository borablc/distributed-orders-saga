package com.borablc.saga.inventory.config;

import com.borablc.saga.common.Queues;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    // Exchanges
    @Bean
    public TopicExchange commandExchange(){
        return new TopicExchange(Queues.COMMAND_EXCHANGE);
    }
    @Bean
    public TopicExchange retryExchange(){
        return new TopicExchange(Queues.RETRY_EXCHANGE);
    }
    @Bean
    public TopicExchange dlxExchange(){
        return new TopicExchange(Queues.DLX);
    }

    //Queues
    @Bean
    public Queue commandQueue(){
        return QueueBuilder.durable(Queues.INVENTORY_QUEUE).build();
    }
    @Bean
    public Queue retryQueue(){
        return QueueBuilder.durable(Queues.INVENTORY_RETRY_QUEUE)
                .deadLetterExchange(Queues.COMMAND_EXCHANGE)
                .ttl(5000)
                .build();
    }
    @Bean
    public Queue parkingLotQueue(){
        return QueueBuilder.durable(Queues.INVENTORY_PARKING_LOT).build();
    }

    //Bindings
    @Bean
    public Binding commandBinding(){
        return BindingBuilder
                .bind(commandQueue())
                .to(commandExchange())
                .with(Queues.RK_INVENTORY_ALL);
    }
    @Bean
    public Binding retryBinding(){
        return BindingBuilder
                .bind(retryQueue())
                .to(retryExchange())
                .with(Queues.RK_INVENTORY_ALL);
    }
    @Bean
    public Binding dlxBinding(){
        return BindingBuilder
                .bind(parkingLotQueue())
                .to(dlxExchange())
                .with(Queues.RK_INVENTORY_ALL);
    }
}
