package com.mysillydreams.inventorycore.controller;

import com.mysillydreams.inventorycore.domain.StockLevel;
import com.mysillydreams.inventorycore.repository.StockLevelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/inventory") // As per guide
@RequiredArgsConstructor
// @ConditionalOnProperty(name = "inventory.controller.internal.enabled", havingValue = "true", matchIfMissing = true) // Optional: make it configurable
public class InternalInventoryController {

    private final StockLevelRepository stockLevelRepository; // Renamed from 'repo' to follow convention

    @GetMapping("/{sku}")
    public ResponseEntity<StockLevel> getStockLevelBySku(@PathVariable String sku) {
        return stockLevelRepository.findById(sku)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Optional: Add other internal endpoints if needed, e.g., to list all stock, or manually adjust stock for testing.
    // Example:
    /*
    @PostMapping("/{sku}/add")
    @Transactional // If this controller is enabled in test/dev, ensure transactional safety
    public ResponseEntity<StockLevel> addStock(@PathVariable String sku, @RequestParam int quantity) {
        if (quantity <= 0) {
            return ResponseEntity.badRequest().body(null); // Or throw exception
        }
        StockLevel stock = stockLevelRepository.findById(sku)
            .orElseGet(() -> {
                // If SKU doesn't exist, create it - depends on business logic
                StockLevel newStock = new StockLevel(sku, 0, 0, 0L, null);
                // Set initial available quantity or require it to exist
                return newStock;
            });
        stock.setAvailable(stock.getAvailable() + quantity);
        return ResponseEntity.ok(stockLevelRepository.save(stock));
    }
    */
}
