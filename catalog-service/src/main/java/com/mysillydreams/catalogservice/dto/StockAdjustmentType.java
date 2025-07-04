package com.mysillydreams.catalogservice.dto;

public enum StockAdjustmentType {
    RECEIVE, // Increase stock (e.g., new inventory arrival)
    ISSUE,   // Decrease stock (e.g., sale, internal use, damage prior to cart reservation)
    ADJUSTMENT // Manual correction (can be positive or negative)
}
