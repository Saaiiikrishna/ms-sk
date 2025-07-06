package com.mysillydreams.inventoryapi.service;

import com.mysillydreams.inventoryapi.dto.AdjustStockRequest;
import com.mysillydreams.inventoryapi.dto.ReservationRequestDto;
import com.mysillydreams.inventoryapi.dto.StockLevelDto;

public interface InventoryService {
  StockLevelDto getStock(String sku);
  void adjustStock(AdjustStockRequest req);
  void reserve(ReservationRequestDto req);
}
