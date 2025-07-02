package com.mysillydreams.userservice.repository.inventory;

import com.mysillydreams.userservice.domain.inventory.InventoryItem;
import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    /**
     * Finds all inventory items associated with a given inventory profile (owner).
     *
     * @param owner The InventoryProfile that owns the items.
     * @return A list of {@link InventoryItem} belonging to the owner.
     */
    List<InventoryItem> findByOwner(InventoryProfile owner);

    /**
     * Finds all inventory items associated with a given inventory profile ID.
     *
     * @param ownerId The UUID of the InventoryProfile.
     * @return A list of {@link InventoryItem} belonging to the owner with the given ID.
     */
    List<InventoryItem> findByOwnerId(UUID ownerId);

    /**
     * Finds an inventory item by its SKU (Stock Keeping Unit).
     * SKU is unique across all items as per current entity definition.
     *
     * @param sku The SKU to search for.
     * @return An {@link Optional} containing the {@link InventoryItem} if found, or empty otherwise.
     */
    Optional<InventoryItem> findBySku(String sku);

    /**
     * Finds an inventory item by its SKU for a specific owner (InventoryProfile).
     * Useful if SKUs are only unique per owner/profile.
     *
     * @param owner The InventoryProfile that owns the item.
     * @param sku The SKU to search for within that owner's items.
     * @return An {@link Optional} containing the {@link InventoryItem} if found.
     */
    Optional<InventoryItem> findByOwnerAndSku(InventoryProfile owner, String sku);
}
