package com.shopmart.order.event;

import com.shopmart.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaListener {
    private final OrderService orderService;

    @KafkaListener(topics = KafkaTopics.ORDER, groupId = "order-saga")
    @Transactional
    public void handle(OrderEvent event) {
        switch (event.getType()) {
            case INVENTORY_FAILED -> orderService.cancelOrder(event.getOrderId(), event.getMessage());
            case PAYMENT_COMPLETED -> orderService.completeOrder(event.getOrderId());
            case INVENTORY_RELEASED -> orderService.cancelOrder(event.getOrderId(), "Payment failed: " + event.getMessage());
            case ORDER_CANCELLED -> orderService.cancelOrder(event.getOrderId(), event.getMessage());
            case ORDER_CREATED, INVENTORY_RESERVED, PAYMENT_FAILED -> { }
        }
    }
}
