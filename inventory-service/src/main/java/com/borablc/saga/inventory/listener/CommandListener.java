package com.borablc.saga.inventory.listener;

import com.borablc.saga.common.Headers;
import com.borablc.saga.common.MessageTypes;
import com.borablc.saga.common.Queues;
import com.borablc.saga.common.command.ConfirmStock;
import com.borablc.saga.common.command.ReleaseStock;
import com.borablc.saga.common.command.ReserveStock;
import com.borablc.saga.inventory.service.InventoryService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class CommandListener {

    private static final int MAX_RETRIES = 3;
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    public CommandListener(InventoryService inventoryService,
                           ObjectMapper objectMapper,
                           RabbitTemplate rabbitTemplate) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = Queues.INVENTORY_QUEUE)
    public void onCommand(Message message, Channel channel) {
        //Steps:
        //1. Take the delivery tag -> Because we are using manual acknowledgement
        //2. Read the message type
        //3. Deserialize the message and direct to the appropriate service or send to the dead letter queue if unknown or null
        //4. Acknowledge the message if successful
        //5. If unsuccessful, requeue the message or send to the dead letter queue

        //Step 1
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        //Step 2
        String messageType = message.getMessageProperties().getHeader(Headers.MESSAGE_TYPE);

        //Step 3
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            switch (messageType) {
                case MessageTypes.RESERVE_STOCK ->
                        inventoryService.reserveStock(objectMapper.readValue(payload, ReserveStock.class));
                case MessageTypes.RELEASE_STOCK ->
                        inventoryService.releaseStock(objectMapper.readValue(payload, ReleaseStock.class));
                case MessageTypes.CONFIRM_STOCK ->
                        inventoryService.confirmStock(objectMapper.readValue(payload, ConfirmStock.class));
                case null -> {
                    log.warn("Message type is null");
                    sendToDLXAndAck(message, channel, deliveryTag);
                    return;
                }
                default -> {
                    log.warn("Unknown message type: {}", messageType);
                    sendToDLXAndAck(message, channel, deliveryTag);
                    return;
                }
            }
            //Step 4
            channel.basicAck(deliveryTag, false);

            //Step 5
        } catch (JacksonException e) {
            log.error("Malformed payload, parking message", e);
            sendToDLXAndAck(message, channel, deliveryTag);
        } catch (Exception e) {
            handleFailureAndAck(message, channel, deliveryTag, e);
        }
    }

    private void sendToDLXAndAck(Message message, Channel channel, long deliveryTag) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        rabbitTemplate.send(Queues.DLX, routingKey, message);
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException ackError) {
            // Acknowledgement failed after sending to DLX.
            // This means the message will sit in the DLQ and also be redelivered. So this message will be copied.
            // However, the dedup mechanism will protect us from the replica.
            log.error("Failed to acknowledge message", ackError);
        }
    }

    private void handleFailureAndAck(Message message, Channel channel, long deliveryTag, Exception e) {
        Integer header = message.getMessageProperties().getHeader(Headers.RETRY_COUNT);
        int retryCount = header == null ? 0 : header;

        if (retryCount >= MAX_RETRIES) {
            log.error("Message failed after {} retries, parking message", retryCount, e);
            sendToDLXAndAck(message, channel, deliveryTag);
            return;
        }
        log.warn("Retry {} of {}", retryCount + 1, MAX_RETRIES, e);
        message.getMessageProperties().setHeader(Headers.RETRY_COUNT, retryCount + 1);
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        rabbitTemplate.send(Queues.RETRY_EXCHANGE, routingKey, message);
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException ackError) {
            // Acknowledgement failed after sending to Retry Exchange.
            // This means the message will be sent to Inventory Queue again by us and also be delivered by RabbitMQ.
            // However, the dedup mechanism will protect us from the replica created by RabbitMQ.
            log.error("Failed to acknowledge message", ackError);

        }
    }
}
