package com.borablc.saga.inventory.service;


import com.borablc.saga.common.MessageTypes;
import com.borablc.saga.common.OrderItem;
import com.borablc.saga.common.command.ReleaseStock;
import com.borablc.saga.common.command.ReserveStock;
import com.borablc.saga.common.event.StockReleased;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
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
        //Positive scenarios:
        //1. Command is already processed -> ignore (duplicate reservation)
        //2. Reservation already exists -> ignore (duplicate reservation)
        //3. SKU not found -> publish failure event
        //4. Insufficient stock -> publish failure event
        //5. Reserve stock -> update stock, create reservation and write event to outbox


        Instant now = Instant.now();
        UUID replyMessageId = UUID.randomUUID();

        // Scenario 1
        if (processedMessageRepository.markProcessed(command.messageId(), now) == 0) {
            return;
        }

        // Scenario 2
        if (reservationRepository.existsById(command.reservationId())){
            return;
        }

        // Grouping if same skus are requested more than once
        Map<String, Integer> requested = command.lines().stream()
                .collect(Collectors.groupingBy(
                        OrderItem::sku,
                        Collectors.summingInt(OrderItem::quantity)
                ));
        Map<String, StockItem> stockItems = new HashMap<>();

        // Scenario 3, 4
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

        // Scenario 5 (Update stock)
        requested.forEach((sku, quantity) -> {
            StockItem stockItem = stockItems.get(sku);
            stockItem.setAvailable(stockItem.getAvailable() - quantity);
            stockItem.setReserved(stockItem.getReserved() + quantity);
        });

        //Scenario 5 (Create reservation)
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

        // Scenario 5 (Write event to outbox)
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
        // 4 possible scenarios:
        // 1. Command is already processed -> ignore (duplicate release)
        // 2. Reservation not found -> ignore, reservation was never created (reserve failed or its reply was lost), nothing to compensate
        // 3. Reservation found but not active -> ignore (duplicate release)
        // 4. Reservation found and active -> update stock, update reservation status and write event to outbox

        Instant now = Instant.now();
        // Scenario 1
        if (processedMessageRepository.markProcessed(command.messageId(), now) == 0) {
            return;
        }

        // Scenario 2
        Reservation reservation = reservationRepository.findById(command.reservationId()).orElse(null);
        if (reservation == null) {
            return;
        }

        //  Scenario 3
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            return;
        }

        // Scenario 4 (Update stock)
        for(ReservationLine reservationLine : reservation.getReservationLines()){
            StockItem stockItem = stockItemRepository.findById(reservationLine.getSku()).orElse(null);
            if (stockItem == null) {
                // SKU might have been removed between reservation and release, worth logging.
                log.warn("Stock item not found for sku: {}", reservationLine.getSku());
                continue;
            }
            stockItem.setAvailable(stockItem.getAvailable() + reservationLine.getQuantity());
            stockItem.setReserved(stockItem.getReserved() - reservationLine.getQuantity());
        }

        //  Scenario 4 (Update reservation status)
        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setReleasedAt(now);

        //Scenario 4 (Write event to outbox)
        UUID replyMessageId = UUID.randomUUID();
        StockReleased stockReleased = new StockReleased(
                replyMessageId,
                command.orderId(),
                command.reservationId(),
                now
        );
        outboxWriter.write(replyMessageId, command.orderId(), MessageTypes.STOCK_RELEASED, stockReleased);
    }
}
