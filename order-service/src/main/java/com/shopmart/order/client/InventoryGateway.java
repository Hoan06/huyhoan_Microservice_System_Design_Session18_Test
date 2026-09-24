package com.shopmart.order.client;

import com.shopmart.order.dto.ProductDto;
import com.shopmart.order.dto.StockRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryGateway {
    private final InventoryClient inventoryClient;

    @CircuitBreaker(name = "inventory", fallbackMethod = "productFallback")
    public ProductDto getProduct(Long id) {
        return inventoryClient.getProduct(id);
    }

    @CircuitBreaker(name = "inventory", fallbackMethod = "stockFallback")
    public ProductDto decrease(Long id, StockRequest request) {
        return inventoryClient.decrease(id, request);
    }

    public ProductDto increase(Long id, StockRequest request) {
        return inventoryClient.increase(id, request);
    }

    private ProductDto productFallback(Long id, Throwable error) {
        log.error("Inventory circuit breaker fallback for productId={}: {}", id, error.getMessage());
        throw new IllegalStateException("Inventory service unavailable; order cannot be priced", error);
    }

    private ProductDto stockFallback(Long id, StockRequest request, Throwable error) {
        log.error("Inventory circuit breaker fallback for stock reservation productId={}: {}", id, error.getMessage());
        throw new IllegalStateException("Inventory service unavailable; reservation failed", error);
    }
}
