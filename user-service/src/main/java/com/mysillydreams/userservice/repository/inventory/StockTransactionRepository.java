package com.mysillydreams.userservice.repository.inventory;

import com.mysillydreams.userservice.domain.inventory.InventoryItem;
import com.mysillydreams.userservice.domain.inventory.StockTransaction;
import com.mysillydreams.userservice.domain.inventory.TransactionType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockTransactionRepository extends JpaRepository<StockTransaction, UUID> {

    /**
     * Finds all stock transactions for a given inventory item, typically ordered by timestamp.
     *
     * @param item The InventoryItem for which to retrieve transactions.
     * @param sort The sorting criteria (e.g., by timestamp).
     * @return A list of {@link StockTransaction} for the item.
     */
    List<StockTransaction> findByItem(InventoryItem item, Sort sort);

    /**
     * Finds all stock transactions for a given inventory item ID, typically ordered by timestamp.
     *
     * @param itemId The UUID of the InventoryItem.
     * @param sort The sorting criteria.
     * @return A list of {@link StockTransaction} for the item with the given ID.
     */
    List<StockTransaction> findByItemId(UUID itemId, Sort sort);

    /**
     * Finds stock transactions for a given item and of a specific type.
     *
     * @param item The InventoryItem.
     * @param type The TransactionType.
     * @param sort Sorting criteria.
     * @return A list of matching {@link StockTransaction}.
     */
    List<StockTransaction> findByItemAndType(InventoryItem item, TransactionType type, Sort sort);

    /**
     * Finds stock transactions that occurred within a specific time range for an item.
     *
     * @param item      The InventoryItem.
     * @param startTime The start of the time range (inclusive).
     * @param endTime   The end of the time range (exclusive).
     * @param sort      Sorting criteria.
     * @return A list of matching {@link StockTransaction}.
     */
    List<StockTransaction> findByItemAndTimestampBetween(InventoryItem item, Instant startTime, Instant endTime, Sort sort);
}
