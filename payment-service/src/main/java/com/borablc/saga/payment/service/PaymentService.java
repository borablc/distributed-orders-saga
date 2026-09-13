package com.borablc.saga.payment.service;

import com.borablc.saga.common.MessageTypes;
import com.borablc.saga.common.command.ChargePayment;
import com.borablc.saga.common.command.RefundPayment;
import com.borablc.saga.common.event.PaymentCharged;
import com.borablc.saga.common.event.PaymentFailed;
import com.borablc.saga.common.event.PaymentRefunded;
import com.borablc.saga.payment.domain.Payment;
import com.borablc.saga.payment.domain.PaymentRepository;
import com.borablc.saga.payment.domain.PaymentStatus;
import com.borablc.saga.payment.inbox.ProcessedMessageRepository;
import com.borablc.saga.payment.outbox.OutboxWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class PaymentService {

    private final OutboxWriter outboxWriter;
    private final PaymentRepository paymentRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final BigDecimal threshold;

    public PaymentService(OutboxWriter outboxWriter,
                          PaymentRepository paymentRepository,
                          ProcessedMessageRepository processedMessageRepository,
                          @Value("${payment.decline-above:500.00}") BigDecimal threshold) {
        this.outboxWriter = outboxWriter;
        this.paymentRepository = paymentRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.threshold = threshold;
    }

    @Transactional
    public void chargePayment(ChargePayment command){
        //Possible scenarios:
        //1. Command is already processed -> ignore (duplicate charge)
        //2. Payment already exists -> ignore (duplicate charge)
        //3. Payment is above threshold -> publish failure event (Flaky by design to test saga compensation)
        //4. Command is valid -> create payment and write event to outbox

        //Scenario 1
        Instant now = Instant.now();
        if (processedMessageRepository.markProcessed(command.messageId(), now) == 0) {
            return;
        }

        //Scenario 2
        if (paymentRepository.existsById(command.chargeId())){
            return;
        }

        //Scenario 3
        UUID replyMessageId = UUID.randomUUID();
        if (command.amount().compareTo(threshold) > 0){
            publishFailure(replyMessageId, command, "AMOUNT_EXCEEDS_LIMIT", now);
            return;
        }

        //Scenario 4 (Create payment)
        Payment payment = new Payment();
        payment.setId(command.chargeId());
        payment.setOrderId(command.orderId());
        payment.setCustomerId(command.customerId());
        payment.setStatus(PaymentStatus.CHARGED);
        payment.setAmount(command.amount());
        payment.setCreatedAt(now);
        paymentRepository.save(payment);

        //Scenario 4 (Write event to Outbox)
        PaymentCharged entry = new PaymentCharged(
                replyMessageId,
                command.orderId(),
                command.chargeId(),
                command.amount(),
                now
        );
        outboxWriter.write(replyMessageId, command.orderId(), MessageTypes.PAYMENT_CHARGED, entry);
    }

    @Transactional
    public void refundPayment(RefundPayment command){
        //Possible scenarios:
        //1. Command is already processed -> ignore (duplicate refund)
        //2. Payment not found -> ignore, charging never created (charge failed or its reply was lost), nothing to compensate
        //3. Payment already REFUNDED -> ignore (duplicate refund)
        //4. Payment found and CHARGED -> update payment and write event to outbox

        //Scenario 1
        Instant now = Instant.now();
        if (processedMessageRepository.markProcessed(command.messageId(), now) == 0) {
            return;
        }

        //Scenario 2
        Payment payment = paymentRepository.findById(command.chargeId()).orElse(null);
        if (payment == null) {
            return;
        }

        //Scenario 3
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }

        //Scenario 4 (Update payment)
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(now);

        //Scenario 4 (Write event to outbox)
        UUID replyMessageId = UUID.randomUUID();
        PaymentRefunded entry = new PaymentRefunded(
                replyMessageId,
                command.orderId(),
                command.chargeId(),
                payment.getAmount(),
                now
        );
        outboxWriter.write(replyMessageId, command.orderId(), MessageTypes.PAYMENT_REFUNDED, entry);
    }

    private void publishFailure(UUID messageId, ChargePayment command, String reason, Instant now){
        PaymentFailed entry = new PaymentFailed(
                messageId,
                command.orderId(),
                reason,
                now);
        outboxWriter.write(messageId, command.orderId(), MessageTypes.PAYMENT_FAILED, entry);
    }

}
