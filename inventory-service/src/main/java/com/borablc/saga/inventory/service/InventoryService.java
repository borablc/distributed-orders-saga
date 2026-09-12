package com.borablc.saga.inventory.service;


import com.borablc.saga.common.MessageTypes;
import com.borablc.saga.common.OrderItem;
import com.borablc.saga.common.command.ReleaseStock;
import com.borablc.saga.common.command.ReserveStock;
import com.borablc.saga.common.event.StockReservationFailed;
import com.borablc.saga.common.event.StockReserved;
import com.borablc.saga.inventory.domain.Reservation;
import com.borablc.saga.inventory.domain.ReservationLine;
import com.borablc.saga.inventory.domain.ReservationRepository;
import com.borablc.saga.inventory.domain.ReservationStatus;
import com.borablc.saga.inventory.domain.StockItem;
import com.borablc.saga.inventory.domain.StockItemRepository;
import com.borablc.saga.inventory.inbox.ProcessedMessageRepository;
import com.borablc.saga.inventory.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InventoryService {
    private final StockItemRepository stockItemRepository;
    private final ReservationRepository reservationRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final OutboxWriter outboxWriter;

    public InventoryService(StockItemRepository stockItemRepository,
                            ReservationRepository reservationRepository,
                            ProcessedMessageRepository processedMessageRepository,
                            OutboxWriter outboxWriter) {
        this.stockItemRepository = stockItemRepository;
        this.reservationRepository = reservationRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public void reserveStock(ReserveStock command){
        Instant now = Instant.now();
        UUID replyMessageId = UUID.randomUUID();
        if (processedMessageRepository.markProcessed(command.messageId(), now) == 0) {
            return;
        }

        if (reservationRepository.existsById(command.reservationId())){
            return; // ignore duplicate reservations
        }

        Map<String, Integer> requested = command.lines().stream()
                .collect(Collectors.groupingBy(
                        OrderItem::sku,
                        Collectors.summingInt(OrderItem::quantity)
                ));
        Map<String, StockItem> stockItems = new HashMap<>();

        for (Map.Entry<String, Integer> entry : requested.entrySet()) {
            StockItem stockItem = stockItemRepository.findById(entry.getKey()).orElse(null);
            if (stockItem == null) {
                publishFailure(replyMessageId, command, "SKU_NOT_FOUND", now);
                return;
            }
            if (stockItem.getAvailable() < entry.getValue()) {
                publishFailure(replyMessageId, command, "INSUFFICIENT_STOCK", now);
                return;
            }
            stockItems.put(entry.getKey(), stockItem);
        }

        requested.forEach((sku, quantity) -> {
            StockItem stockItem = stockItems.get(sku);
            stockItem.setAvailable(stockItem.getAvailable() - quantity);
            stockItem.setReserved(stockItem.getReserved() + quantity);
        });

        Reservation reservation = new Reservation();
        reservation.setId(command.reservationId());
        reservation.setOrderId(command.orderId());
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setCreatedAt(now);
        requested.forEach((sku, quantity) -> {
            ReservationLine reservationLine = new ReservationLine();
            reservationLine.setSku(sku);
            reservationLine.setQuantity(quantity);
            reservation.addLine(reservationLine);
        });
        reservationRepository.save(reservation);

        StockReserved stockReserved = new StockReserved(
                replyMessageId,
                command.orderId(),
                reservation.getId(),
                now
        );
        outboxWriter.write(replyMessageId, command.orderId(), MessageTypes.STOCK_RESERVED, stockReserved);
    }

    private void publishFailure(UUID messageId, ReserveStock command, String reason, Instant now){
        StockReservationFailed event = new StockReservationFailed(
                messageId,
                command.orderId(),
                reason,
                now);
        outboxWriter.write(messageId, command.orderId(), MessageTypes.STOCK_RESERVATION_FAILED, event);
    }

    @Transactional
    public void releaseStock(ReleaseStock command){

    }
}
