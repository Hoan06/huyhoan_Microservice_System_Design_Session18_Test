package com.shopmart.inventory.event;

import com.shopmart.inventory.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventorySagaListener {
    private final ProductService productService;
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @KafkaListener(topics = KafkaTopics.ORDER, groupId = "inventory-saga")
    @Transactional
    public void handle(OrderEvent event) {
        if (event.getType() == SagaEventType.ORDER_CREATED) {
            try {
                productService.decreaseStock(event.getProductId(), event.getQuantity());
                event.setType(SagaEventType.INVENTORY_RESERVED);
                event.setMessage("Inventory reserved");
                log.info("Inventory reserved for orderId={}", event.getOrderId());
            } catch (Exception ex) {
                event.setType(SagaEventType.INVENTORY_FAILED);
                event.setMessage(ex.getMessage());
                log.error("Inventory reservation failed for orderId={}: {}", event.getOrderId(), ex.getMessage());
            }
            kafkaTemplate.send(KafkaTopics.ORDER, event.getOrderId().toString(), event);
        } else if (event.getType() == SagaEventType.PAYMENT_FAILED) {
            try {
                productService.increaseStock(event.getProductId(), event.getQuantity());
                event.setType(SagaEventType.INVENTORY_RELEASED);
                log.info("Inventory compensated for orderId={}", event.getOrderId());
            } catch (Exception ex) {
                event.setType(SagaEventType.INVENTORY_FAILED);
                event.setMessage("Inventory compensation failed: " + ex.getMessage());
                log.error("Inventory compensation failed for orderId={}", event.getOrderId(), ex);
            }
            kafkaTemplate.send(KafkaTopics.ORDER, event.getOrderId().toString(), event);
        } else if (event.getType() == SagaEventType.ORDER_CANCELLED) {
            log.warn("Order cancellation acknowledged for orderId={}", event.getOrderId());
        } else if (event.getType() == SagaEventType.INVENTORY_RELEASED) {
            log.info("Inventory release confirmed for orderId={}", event.getOrderId());
        }
    }
}
