package com.mysillydreams.userservice.domain.inventory;

public enum TransactionType {
    RECEIVE,    // New stock added (e.g., from supplier, manufacturing)
    ISSUE,      // Stock removed (e.g., for a sales order, internal consumption)
    ADJUSTMENT  // Manual correction (e.g., due to stock count discrepancy, damage)
    // Consider other types like RETURN_TO_SUPPLIER, INTERNAL_TRANSFER, etc. if needed
}
