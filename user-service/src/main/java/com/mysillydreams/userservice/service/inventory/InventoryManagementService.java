package com.mysillydreams.userservice.service.inventory;

import com.mysillydreams.userservice.domain.inventory.InventoryItem;
import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import com.mysillydreams.userservice.domain.inventory.StockTransaction;
import com.mysillydreams.userservice.domain.inventory.TransactionType;
import com.mysillydreams.userservice.dto.inventory.InventoryItemDto;
import com.mysillydreams.userservice.dto.inventory.StockAdjustmentRequest;
import com.mysillydreams.userservice.repository.inventory.InventoryItemRepository;
import com.mysillydreams.userservice.repository.inventory.InventoryProfileRepository; // To verify profile exists
import com.mysillydreams.userservice.repository.inventory.StockTransactionRepository;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InventoryManagementService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryManagementService.class);

    private final InventoryItemRepository itemRepository;
    private final StockTransactionRepository transactionRepository;
    private final InventoryProfileRepository inventoryProfileRepository; // To validate profileId
    private final InventoryKafkaClient inventoryKafkaClient;

    @Autowired
    public InventoryManagementService(InventoryItemRepository itemRepository,
                                      StockTransactionRepository transactionRepository,
                                      InventoryProfileRepository inventoryProfileRepository,
                                      InventoryKafkaClient inventoryKafkaClient) {
        this.itemRepository = itemRepository;
        this.transactionRepository = transactionRepository;
        this.inventoryProfileRepository = inventoryProfileRepository;
        this.inventoryKafkaClient = inventoryKafkaClient;
    }

    /**
     * Adds a new inventory item for a given inventory profile.
     *
     * @param profileId The UUID of the inventory profile (owner).
     * @param dto       The InventoryItemDto containing item details.
     * @return The created InventoryItemDto.
     * @throws EntityNotFoundException if the inventory profile does not exist.
     * @throws IllegalArgumentException if an item with the same SKU already exists.
     */
    @Transactional
    public InventoryItemDto addItem(UUID profileId, InventoryItemDto dto) {
        Assert.notNull(profileId, "Inventory Profile ID cannot be null.");
        Assert.notNull(dto, "InventoryItemDto cannot be null.");
        Assert.hasText(dto.getSku(), "SKU cannot be blank.");
        Assert.hasText(dto.getName(), "Item name cannot be blank.");
        // Assert.notNull(dto.getQuantityOnHand(), "Quantity on hand cannot be null."); // DTO defaults to 0
        Assert.notNull(dto.getReorderLevel(), "Reorder level cannot be null.");


        logger.info("Attempting to add item with SKU: {} for Profile ID: {}", dto.getSku(), profileId);

        InventoryProfile owner = inventoryProfileRepository.findById(profileId)
                .orElseThrow(() -> new EntityNotFoundException("InventoryProfile not found with ID: " + profileId));

        // Check for SKU uniqueness (globally or per profile, depending on requirements)
        // Current InventoryItem.sku is globally unique.
        if (itemRepository.findBySku(dto.getSku()).isPresent()) {
            throw new IllegalArgumentException("Inventory item with SKU " + dto.getSku() + " already exists.");
        }
        // If SKU unique per profile:
        // if (itemRepository.findByOwnerAndSku(owner, dto.getSku()).isPresent()) {
        //     throw new IllegalArgumentException("Inventory item with SKU " + dto.getSku() + " already exists for this profile.");
        // }

        InventoryItem item = dto.toEntity(); // Maps basic fields from DTO
        item.setOwner(owner);
        // quantityOnHand defaults to 0 in DTO if not set, which is fine for a new item.
        // If DTO had quantityOnHand, it would be set by toEntity().
        // The scaffold service logic: item.setQuantityOnHand(dto.getQuantityOnHand());
        // DTO has default 0, so this is fine. If DTO can be null, handle it.
        item.setQuantityOnHand(dto.getQuantityOnHand() != null ? dto.getQuantityOnHand() : 0);


        InventoryItem savedItem = itemRepository.save(item);
        logger.info("Inventory item added with ID: {}, SKU: {}", savedItem.getId(), savedItem.getSku());

        // Publish event
        inventoryKafkaClient.publishItemCreated(savedItem);

        return InventoryItemDto.from(savedItem);
    }

    /**
     * Adjusts the stock quantity of an inventory item and records the transaction.
     *
     * @param itemId The UUID of the inventory item to adjust.
     * @param request The StockAdjustmentRequest containing adjustment details.
     * @return The updated InventoryItemDto.
     * @throws EntityNotFoundException if the inventory item does not exist.
     * @throws IllegalArgumentException if the adjustment results in negative stock (unless type is ADJUSTMENT allowing it).
     */
    @Transactional
    public InventoryItemDto adjustStock(UUID itemId, StockAdjustmentRequest request) {
        Assert.notNull(itemId, "Item ID cannot be null.");
        Assert.notNull(request, "StockAdjustmentRequest cannot be null.");
        Assert.notNull(request.getType(), "Transaction type cannot be null.");
        Assert.notNull(request.getQuantity(), "Quantity cannot be null.");
        Assert.isTrue(request.getQuantity() > 0, "Adjustment quantity must be positive."); // As per DTO validation @Min(1)

        logger.info("Attempting to adjust stock for Item ID: {}, Type: {}, Quantity: {}",
                itemId, request.getType(), request.getQuantity());

        InventoryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("InventoryItem not found with ID: " + itemId));

        int quantityChange = request.getQuantity();
        int currentQuantity = item.getQuantityOnHand();
        int newQuantity;

        switch (request.getType()) {
            case RECEIVE:
                newQuantity = currentQuantity + quantityChange;
                break;
            case ISSUE:
                newQuantity = currentQuantity - quantityChange;
                if (newQuantity < 0) {
                    logger.warn("Stock issue for Item ID: {} would result in negative stock ({}). Current: {}, Requested: {}",
                            itemId, newQuantity, currentQuantity, quantityChange);
                    throw new IllegalArgumentException("Stock issue quantity exceeds available stock for item ID: " + itemId);
                }
                break;
            case ADJUSTMENT:
                // The scaffold logic: newQty = item.getQuantityOnHand() + (req.getType()==TransactionType.RECEIVE ? req.getQuantity() : -req.getQuantity());
                // This implies for ADJUSTMENT, it's treated like an ISSUE (subtraction).
                // A more flexible ADJUSTMENT might allow specifying if it's positive or negative.
                // Given current scaffold, an ADJUSTMENT that *increases* stock would need to be a RECEIVE,
                // or this logic needs to be more nuanced for ADJUSTMENT type.
                // Let's assume for now an "ADJUSTMENT" from request means a reduction unless specified otherwise.
                // Or, better: the DTO should perhaps have a signed quantity for adjustments, or a separate "adjustment_type" (INC/DEC).
                // For now, following scaffold's implication:
                logger.warn("Stock adjustment for Item ID: {} treated as a reduction. Original scaffold logic might need refinement for positive adjustments.", itemId);
                newQuantity = currentQuantity - quantityChange; // Assuming adjustment typically means writing off stock
                                                                // If this needs to handle increase, a different type or flag is needed
                // No check for negative stock for pure adjustment, as it might be correcting to a negative if that's allowed by business rules (rarely).
                // Or, if adjustments must keep stock >= 0:
                // if (newQuantity < 0) {
                //    throw new IllegalArgumentException("Stock adjustment results in negative quantity for item ID: " + itemId);
                // }
                break;
            default:
                throw new IllegalArgumentException("Unsupported transaction type: " + request.getType());
        }

        item.setQuantityOnHand(newQuantity);
        InventoryItem updatedItem = itemRepository.save(item);

        // Record transaction
        StockTransaction transaction = new StockTransaction();
        transaction.setItem(updatedItem);
        transaction.setType(request.getType());
        transaction.setQuantity(request.getQuantity()); // Store the absolute quantity of change
        // transaction.setPerformedBy(...); // If user context is available and needed
        // transaction.setReferenceId(request.getReason()); // If reason is a reference
        transactionRepository.save(transaction);

        logger.info("Stock adjusted for Item ID: {}. New Quantity: {}. Transaction ID: {}",
                updatedItem.getId(), updatedItem.getQuantityOnHand(), transaction.getId());

        // Publish event
        inventoryKafkaClient.publishStockAdjusted(updatedItem, transaction);

        return InventoryItemDto.from(updatedItem);
    }

    /**
     * Lists all inventory items for a given inventory profile.
     *
     * @param profileId The UUID of the inventory profile.
     * @return A list of InventoryItemDto.
     * @throws EntityNotFoundException if the inventory profile does not exist.
     */
    @Transactional(readOnly = true)
    public List<InventoryItemDto> listItemsByProfileId(UUID profileId) {
        Assert.notNull(profileId, "Inventory Profile ID cannot be null.");
        logger.debug("Listing items for Profile ID: {}", profileId);

        // Ensure profile exists before querying items by it, or let findByOwnerId handle it
        if (!inventoryProfileRepository.existsById(profileId)) {
             throw new EntityNotFoundException("InventoryProfile not found with ID: " + profileId);
        }
        // InventoryProfile ownerRef = new InventoryProfile(profileId); // Create a reference for query

        return itemRepository.findByOwnerId(profileId) // Using findByOwnerId
                .stream()
                .map(InventoryItemDto::from)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a specific inventory item by its ID.
     * @param itemId The UUID of the inventory item.
     * @return The InventoryItemDto.
     * @throws EntityNotFoundException if item not found.
     */
    @Transactional(readOnly = true)
    public InventoryItemDto getItemById(UUID itemId) {
        Assert.notNull(itemId, "Item ID cannot be null.");
        InventoryItem item = itemRepository.findById(itemId)
            .orElseThrow(() -> new EntityNotFoundException("InventoryItem not found with ID: " + itemId));
        return InventoryItemDto.from(item);
    }

    /**
     * Updates an existing inventory item.
     * (Stub for now)
     * @param itemId The UUID of the item to update.
     * @param dto The DTO with updated information.
     * @return The updated InventoryItemDto.
     */
    @Transactional
    public InventoryItemDto updateItem(UUID itemId, InventoryItemDto dto) {
        Assert.notNull(itemId, "Item ID cannot be null.");
        Assert.notNull(dto, "InventoryItemDto for update cannot be null.");
        logger.info("Attempting to update item ID: {}", itemId);

        InventoryItem item = itemRepository.findById(itemId)
            .orElseThrow(() -> new EntityNotFoundException("InventoryItem not found with ID: " + itemId));

        // Update fields from DTO - be selective about what can be updated
        if (dto.getName() != null) item.setName(dto.getName());
        if (dto.getDescription() != null) item.setDescription(dto.getDescription());
        if (dto.getReorderLevel() != null) item.setReorderLevel(dto.getReorderLevel());
        // SKU usually not updatable. QuantityOnHand updated via adjustStock.

        InventoryItem updatedItem = itemRepository.save(item);
        logger.info("Item ID: {} updated.", itemId);
        // Consider if an item.updated event is needed. The scaffold only had item.created.
        return InventoryItemDto.from(updatedItem);
    }

    /**
     * Lists stock transactions for a given item.
     * (Stub for now)
     * @param itemId The UUID of the inventory item.
     * @return List of StockTransaction (or DTOs).
     */
    @Transactional(readOnly = true)
    public List<StockTransaction> listTransactionsForItem(UUID itemId) {
        Assert.notNull(itemId, "Item ID cannot be null.");
        logger.debug("Listing transactions for item ID: {}", itemId);
        // Ensure item exists
        if (!itemRepository.existsById(itemId)) {
            throw new EntityNotFoundException("InventoryItem not found with ID: " + itemId);
        }
        // InventoryItem itemRef = new InventoryItem(); itemRef.setId(itemId);
        return transactionRepository.findByItemId(itemId, Sort.by(Sort.Direction.DESC, "timestamp"));
    }
}
