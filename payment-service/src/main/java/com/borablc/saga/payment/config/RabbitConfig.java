package com.borablc.saga.payment.config;

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
    public Queue paymentQueue(){
        return QueueBuilder.durable(Queues.PAYMENT_QUEUE).build();
    }
    @Bean
    public Queue paymentRetryQueue(){
        return QueueBuilder.durable(Queues.PAYMENT_RETRY_QUEUE)
                .deadLetterExchange(Queues.COMMAND_EXCHANGE)
                .ttl(5000)
                .build();
    }
    @Bean
    public Queue paymentParkingLotQueue(){
        return QueueBuilder.durable(Queues.PAYMENT_PARKING_LOT).build();
    }

    //Bindings
    @Bean
    public Binding commandBinding(){
        return BindingBuilder
                .bind(paymentQueue())
                .to(commandExchange())
                .with(Queues.RK_PAYMENT_ALL);
    }
    @Bean
    public Binding retryBinding(){
        return BindingBuilder
                .bind(paymentRetryQueue())
                .to(retryExchange())
                .with(Queues.RK_PAYMENT_ALL);
    }
    @Bean
    public Binding dlxBinding(){
        return BindingBuilder
                .bind(paymentParkingLotQueue())
                .to(dlxExchange())
                .with(Queues.RK_PAYMENT_ALL);
    }
}
