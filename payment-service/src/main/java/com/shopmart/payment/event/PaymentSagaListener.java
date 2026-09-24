package com.shopmart.payment.event;

import com.shopmart.payment.dto.PaymentRequest;
import com.shopmart.payment.dto.PaymentResponse;
import com.shopmart.payment.entity.PaymentStatus;
import com.shopmart.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSagaListener {
    private final PaymentService paymentService;
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @KafkaListener(topics = KafkaTopics.ORDER, groupId = "payment-saga")
    public void handle(OrderEvent event) {
        if (event.getType() != SagaEventType.INVENTORY_RESERVED) {
            return;
        }
        PaymentResponse response = paymentService.processPayment(new PaymentRequest(event.getOrderId(), event.getAmount()));
        event.setType(response.getStatus() == PaymentStatus.SUCCESS
                ? SagaEventType.PAYMENT_COMPLETED : SagaEventType.PAYMENT_FAILED);
        event.setMessage(response.getMessage());
        log.info("Payment result={} for orderId={}", event.getType(), event.getOrderId());
        kafkaTemplate.send(KafkaTopics.ORDER, event.getOrderId().toString(), event);
    }
}
