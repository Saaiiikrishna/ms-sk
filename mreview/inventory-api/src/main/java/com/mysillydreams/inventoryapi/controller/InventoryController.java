package com.mysillydreams.inventoryapi.controller;

import com.mysillydreams.inventoryapi.dto.AdjustStockRequest;
import com.mysillydreams.inventoryapi.dto.ReservationRequestDto;
import com.mysillydreams.inventoryapi.dto.StockLevelDto;
import com.mysillydreams.inventoryapi.service.InventoryService; // Changed from InventoryApiService
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
// Removed @PreAuthorize as security is now centralized in KeycloakConfig
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {
  private final InventoryService svc; // Using the interface

  @GetMapping("/{sku}")
  public StockLevelDto getStock(@PathVariable String sku) {
    return svc.getStock(sku);
  }

  @PostMapping("/adjust")
  // @PreAuthorize removed
  public ResponseEntity<Void> adjust(@Valid @RequestBody AdjustStockRequest req) { // Method name changed from adjustStock
    svc.adjustStock(req);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/reserve")
  // @PreAuthorize removed
  public ResponseEntity<Void> reserve(@Valid @RequestBody ReservationRequestDto req) { // Method name changed from reserveSync
    svc.reserve(req); // Calls the renamed service method
    return ResponseEntity.accepted().build();
  }
}
