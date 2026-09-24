package com.shopmart.order.client;

import com.shopmart.order.dto.ProductDto;
import com.shopmart.order.dto.StockRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory-service")
public interface InventoryClient {
    @GetMapping("/api/inventory/products/{id}")
    ProductDto getProduct(@PathVariable("id") Long id);

    @PutMapping("/api/inventory/products/{id}/decrease")
    ProductDto decrease(@PathVariable("id") Long id, @RequestBody StockRequest request);

    @PutMapping("/api/inventory/products/{id}/increase")
    ProductDto increase(@PathVariable("id") Long id, @RequestBody StockRequest request);
}
