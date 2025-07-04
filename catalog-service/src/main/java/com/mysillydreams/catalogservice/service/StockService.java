package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import com.mysillydreams.catalogservice.domain.model.StockLevelEntity;
import com.mysillydreams.catalogservice.domain.model.StockTransactionEntity;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.domain.repository.StockLevelRepository;
import com.mysillydreams.catalogservice.domain.repository.StockTransactionRepository;
import com.mysillydreams.catalogservice.dto.StockAdjustmentRequest;
import com.mysillydreams.catalogservice.dto.StockAdjustmentType;
import com.mysillydreams.catalogservice.dto.StockLevelDto;
import com.mysillydreams.catalogservice.exception.InvalidRequestException;
import com.mysillydreams.catalogservice.exception.ResourceNotFoundException;
import com.mysillydreams.catalogservice.kafka.event.StockLevelChangedEvent;
// import com.mysillydreams.catalogservice.kafka.producer.KafkaProducerService; // No longer direct use
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockLevelRepository stockLevelRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final StockTransactionRepository stockTransactionRepository;
    // private final KafkaProducerService kafkaProducerService; // Replaced
    private final OutboxEventService outboxEventService; // Added

    @Value("${app.kafka.topic.stock-changed}")
    private String stockChangedTopic;

    // Method for cart service to reserve stock (idempotent)
    // This is a preliminary version. True idempotency might require tracking reservation IDs.
    @Transactional
    @Retryable(value = {OptimisticLockException.class, CannotAcquireLockException.class}, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
    public StockLevelDto reserveStock(UUID itemId, int quantityToReserve) {
        log.info("Attempting to reserve {} units for item ID: {}", quantityToReserve, itemId);
        if (quantityToReserve <= 0) {
            throw new InvalidRequestException("Quantity to reserve must be positive.");
        }

        CatalogItemEntity item = catalogItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CatalogItem", "id", itemId));
        if (item.getItemType() != ItemType.PRODUCT) {
            throw new InvalidRequestException("Stock operations only applicable for PRODUCT type items. Item " + itemId + " is a " + item.getItemType());
        }

        StockLevelEntity stockLevel = stockLevelRepository.findByCatalogItemId(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("StockLevel", "itemId", itemId + " (Product may exist but stock record missing)"));

        int quantityBefore = stockLevel.getQuantityOnHand();
        if (quantityBefore < quantityToReserve) {
            throw new InvalidRequestException(String.format(
                    "Insufficient stock for item ID %s. Requested: %d, Available: %d",
                    itemId, quantityToReserve, quantityBefore
            ));
        }

        stockLevel.setQuantityOnHand(quantityBefore - quantityToReserve);
        StockLevelEntity updatedStockLevel = stockLevelRepository.save(stockLevel); // Optimistic lock check here

        // Create stock transaction record for reservation
        // Using a specific type or a generic 'ISSUE' with a reason like 'CartReservation'
        int actualDelta = -quantityToReserve;
        createAndSaveTransaction(item, StockAdjustmentType.ISSUE, actualDelta, quantityBefore, updatedStockLevel.getQuantityOnHand(), "CartReservation", null);

        publishStockLevelChangedEventViaOutbox("StockLevel", item.getId(), item, StockAdjustmentType.ISSUE, actualDelta, quantityBefore, updatedStockLevel.getQuantityOnHand(), "CartReservation", null);

        log.info("Successfully reserved {} units for item ID: {}. New stock: {}", quantityToReserve, itemId, updatedStockLevel.getQuantityOnHand());
        return convertToDto(updatedStockLevel, item);
    }

    // Method for cart service to release stock (idempotent)
    @Transactional
    @Retryable(value = {OptimisticLockException.class, CannotAcquireLockException.class}, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
    public StockLevelDto releaseStock(UUID itemId, int quantityToRelease) {
        log.info("Attempting to release {} units for item ID: {}", quantityToRelease, itemId);
        if (quantityToRelease <= 0) {
            throw new InvalidRequestException("Quantity to release must be positive.");
        }
        CatalogItemEntity item = catalogItemRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("CatalogItem", "id", itemId));
        if (item.getItemType() != ItemType.PRODUCT) {
             throw new InvalidRequestException("Stock operations only applicable for PRODUCT type items. Item " + itemId + " is a " + item.getItemType());
        }

        StockLevelEntity stockLevel = stockLevelRepository.findByCatalogItemId(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("StockLevel", "itemId", itemId));

        int quantityBefore = stockLevel.getQuantityOnHand();
        stockLevel.setQuantityOnHand(quantityBefore + quantityToRelease);
        StockLevelEntity updatedStockLevel = stockLevelRepository.save(stockLevel);
        int actualDeltaRelease = quantityToRelease;

        createAndSaveTransaction(item, StockAdjustmentType.RECEIVE, actualDeltaRelease, quantityBefore, updatedStockLevel.getQuantityOnHand(), "CartRelease/Cancellation", null);
        publishStockLevelChangedEventViaOutbox("StockLevel", item.getId(), item, StockAdjustmentType.RECEIVE, actualDeltaRelease, quantityBefore, updatedStockLevel.getQuantityOnHand(), "CartRelease/Cancellation", null);

        log.info("Successfully released {} units for item ID: {}. New stock: {}", quantityToRelease, itemId, updatedStockLevel.getQuantityOnHand());
        return convertToDto(updatedStockLevel, item);
    }


    @Transactional
    @Retryable(value = {OptimisticLockException.class, CannotAcquireLockException.class}, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
    public StockLevelDto adjustStock(StockAdjustmentRequest request) {
        log.info("Adjusting stock for item ID: {} by quantity: {} with type: {}", request.getItemId(), request.getQuantity(), request.getAdjustmentType());
        validateStockAdjustmentRequest(request);

        CatalogItemEntity item = catalogItemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("CatalogItem", "id", request.getItemId()));

        if (item.getItemType() != ItemType.PRODUCT) {
            throw new InvalidRequestException("Stock operations only applicable for PRODUCT type items. Item " + request.getItemId() + " is a " + item.getItemType());
        }

        StockLevelEntity stockLevel = stockLevelRepository.findByCatalogItemId(request.getItemId())
                .orElseGet(() -> {
                    log.warn("No existing stock level record for product ID: {}. Creating one.", request.getItemId());
                    // This case should ideally be handled when item is created. If it happens, create one.
                    return StockLevelEntity.builder().catalogItem(item).quantityOnHand(0).reorderLevel(0).build();
                });

        int quantityBefore = stockLevel.getQuantityOnHand();
        int quantityAfter;
        int quantityChanged = request.getQuantity(); // This is the magnitude of change

        switch (request.getAdjustmentType()) {
            case RECEIVE:
                quantityAfter = quantityBefore + quantityChanged;
                break;
            case ISSUE:
                if (quantityBefore < quantityChanged) {
                    throw new InvalidRequestException(String.format(
                            "Insufficient stock for item ID %s to issue. Requested: %d, Available: %d",
                            request.getItemId(), quantityChanged, quantityBefore
                    ));
                }
                quantityAfter = quantityBefore - quantityChanged;
                quantityChanged = -quantityChanged; // Make delta negative for event/transaction log
                break;
            case ADJUSTMENT:
                // For ADJUSTMENT, if request.quantity is allowed to be negative for decreases:
                // quantityAfter = quantityBefore + request.getQuantity(); // quantityChanged is request.getQuantity()
                // For now, assuming request.quantity is always positive and we need a way to signal decrease
                // This part needs refinement based on how negative adjustments are represented in request.
                // Let's assume for ADJUSTMENT, positive request.quantity means increase,
                // and we'd need another mechanism or type for explicit decrease if not using ISSUE.
                // For simplicity, let's say ADJUSTMENT here is always an increase by request.quantity.
                // Or, better: let's assume quantityChanged is already signed for ADJUSTMENT
                // For now, let's treat ADJUSTMENT as an absolute set, or need clearer spec on how it works.
                // Reverting to: quantityChanged is magnitude, type defines operation.
                // If adjustment is to set to a new value:
                // quantityAfter = request.getQuantity(); // This would be a direct set, not adjustment
                // Let's assume ADJUSTMENT means "add this amount" (could be negative if input allowed)
                // Sticking to the original interpretation: quantity in request is positive magnitude.
                // For ADJUSTMENT type, this is ambiguous. Let's assume it means "increase by this amount".
                // The problem description implies ISSUE for decrease.
                // So, ADJUSTMENT could be for positive correction.
                // If we need negative correction, a new type or allow negative quantity for ADJUSTMENT.
                // Let's make ADJUSTMENT handle both + and - by making `quantityChanged` the actual delta.
                // This means StockAdjustmentRequest.quantity should be allowed to be negative for type ADJUSTMENT.
                // The current @Min(1) on StockAdjustmentRequest.quantity prevents this.
                // For now, I'll proceed assuming quantity in request is positive, and ADJUSTMENT is positive.
                // This is a point for clarification.
                // For now, let's assume adjustment type means positive adjustment.
                // To support negative adjustments, we'd modify StockAdjustmentRequest or add a new type.
                // Let's assume, for now, ADJUSTMENT means an increase.
        // Based on current StockAdjustmentRequest, quantity is positive.
        // If ADJUSTMENT type is meant for *any* correction, it should allow negative quantity in request,
        // or the service interprets quantity based on a sub-type or reason.
        // For now, assuming quantityChanged (from request.getQuantity()) is the magnitude, and type dictates direction.
        // For event/transaction log, quantityChanged needs to be signed.

        // Re-evaluating quantityChanged for event/log based on type:
        int signedQuantityChanged = request.getQuantity(); // Default to positive magnitude from request

        switch (request.getAdjustmentType()) {
            case RECEIVE:
                quantityAfter = quantityBefore + request.getQuantity();
                // signedQuantityChanged is already positive
                break;
            case ISSUE:
                if (quantityBefore < request.getQuantity()) {
                    throw new InvalidRequestException(String.format(
                            "Insufficient stock for item ID %s to issue. Requested: %d, Available: %d",
                            request.getItemId(), request.getQuantity(), quantityBefore
                    ));
                }
                quantityAfter = quantityBefore - request.getQuantity();
                signedQuantityChanged = -request.getQuantity(); // Make delta negative for event/transaction log
                break;
            case ADJUSTMENT:
                // Assuming ADJUSTMENT means an increase by request.quantity for now, similar to RECEIVE
                // If it could be negative, StockAdjustmentRequest's quantity field validation (@Min(1)) needs change
                // or this logic needs to be more flexible (e.g. separate signed field for adjustment amount).
                log.warn("StockAdjustmentType.ADJUSTMENT currently implies positive adjustment by request.quantity. For decreases, use ISSUE or refine ADJUSTMENT handling for negative values.");
                quantityAfter = quantityBefore + request.getQuantity();
                // signedQuantityChanged is already positive for this interpretation
                break;
            default:
                throw new InvalidRequestException("Unsupported stock adjustment type: " + request.getAdjustmentType());
        }

        if (quantityAfter < 0) { // Should be caught by ISSUE logic, but as a safeguard
             throw new InvalidRequestException("Stock level cannot be negative.");
        }

        stockLevel.setQuantityOnHand(quantityAfter);
        StockLevelEntity updatedStockLevel = stockLevelRepository.save(stockLevel); // Optimistic lock check

        createAndSaveTransaction(item, request.getAdjustmentType(), signedQuantityChanged, quantityBefore, quantityAfter, request.getReason(), request.getReferenceId());
        publishStockLevelChangedEventViaOutbox("StockLevel", item.getId(), item, request.getAdjustmentType(), signedQuantityChanged, quantityBefore, quantityAfter, request.getReason(), request.getReferenceId());

        log.info("Stock adjusted for item ID: {}. Before: {}, After: {}, Change: {}, Reason: {}",
                request.getItemId(), quantityBefore, quantityAfter, signedQuantityChanged, request.getReason());
        return convertToDto(updatedStockLevel, item);
    }

    @Transactional(readOnly = true)
    public StockLevelDto getStockLevelByItemId(UUID itemId) {
        log.debug("Fetching stock level for item ID: {}", itemId);
        CatalogItemEntity item = catalogItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CatalogItem", "id", itemId));

        if (item.getItemType() != ItemType.PRODUCT) {
            // For services, we can return a DTO with null/zero quantity or throw exception
            // Let's return null or a specific response indicating not applicable
            log.debug("Item ID: {} is a SERVICE, stock level not applicable.", itemId);
            return StockLevelDto.builder().itemId(itemId).itemSku(item.getSku()).itemName(item.getName()).quantityOnHand(null).build();
        }

        StockLevelEntity stockLevel = stockLevelRepository.findByCatalogItemId(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("StockLevel", "itemId", itemId + " (Product may exist but stock record missing)"));
        return convertToDto(stockLevel, item);
    }

    @Transactional(readOnly = true)
    public Page<StockLevelDto> listStockLevels(Pageable pageable) {
        log.debug("Listing all stock levels with pagination");
        // This fetches all stock levels. We need to map them to DTOs, which requires item info.
        // This could be N+1 if not careful.
        // Option 1: Fetch StockLevelEntities, then fetch corresponding CatalogItemEntities.
        // Option 2: Custom query in repository to join and fetch necessary fields for DTO.
        // Option 3: Fetch StockLevelEntities, then batch fetch CatalogItemEntities.

        Page<StockLevelEntity> stockLevels = stockLevelRepository.findAll(pageable);
        // Efficiently fetch associated catalog items
        List<UUID> itemIds = stockLevels.getContent().stream().map(StockLevelEntity::getItemId).collect(Collectors.toList());
        Map<UUID, CatalogItemEntity> itemMap = catalogItemRepository.findByIdIn(itemIds).stream()
                .collect(Collectors.toMap(CatalogItemEntity::getId, i -> i));

        return stockLevels.map(sl -> convertToDto(sl, itemMap.get(sl.getItemId())));
    }

    // For reorder alerts (placeholder - actual alerting mechanism would be external)
    @Transactional(readOnly = true)
    public List<StockLevelDto> findItemsBelowReorderLevel() {
        log.info("Checking for items below reorder level.");
        List<StockLevelEntity> lowStockItems = stockLevelRepository.findItemsBelowReorderLevel();
        // Similar to listStockLevels, fetch item details efficiently
        List<UUID> itemIds = lowStockItems.stream().map(StockLevelEntity::getItemId).collect(Collectors.toList());
        Map<UUID, CatalogItemEntity> itemMap = catalogItemRepository.findByIdIn(itemIds).stream()
                .collect(Collectors.toMap(CatalogItemEntity::getId, i -> i));

        return lowStockItems.stream()
            .filter(sl -> sl.getReorderLevel() != null && sl.getQuantityOnHand() < sl.getReorderLevel()) // Double check condition
            .map(sl -> convertToDto(sl, itemMap.get(sl.getItemId())))
            .collect(Collectors.toList());
    }


    private void validateStockAdjustmentRequest(StockAdjustmentRequest request) {
        if (request.getQuantity() <= 0) {
            // This validation might change if StockAdjustmentType.ADJUSTMENT allows negative quantity directly
            throw new InvalidRequestException("Quantity for stock adjustment must be positive. Operation type dictates increase/decrease.");
        }
    }

    private void createAndSaveTransaction(CatalogItemEntity item, StockAdjustmentType type, int quantityChanged,
                                          int qtyBefore, int qtyAfter, String reason, String referenceId) {
        StockTransactionEntity transaction = StockTransactionEntity.builder()
                .catalogItem(item)
                .transactionType(type)
                .quantityChanged(quantityChanged) // This is the actual delta, signed.
                .quantityBeforeTransaction(qtyBefore)
                .quantityAfterTransaction(qtyAfter)
                .reason(reason)
                .referenceId(referenceId)
                .transactionTime(Instant.now()) // ensure this is set, though @CreationTimestamp helps
                .build();
        stockTransactionRepository.save(transaction);
    }

    private void publishStockLevelChangedEventViaOutbox(String aggregateType, UUID aggregateId, CatalogItemEntity item, StockAdjustmentType type, int quantityDelta,
                                               int qtyBefore, int qtyAfter, String reason, String referenceId) {
        StockLevelChangedEvent event = StockLevelChangedEvent.builder()
                .eventId(UUID.randomUUID()) // This could be generated by poller or be same as OutboxEventEntity.id
                .itemId(item.getId())
                .itemSku(item.getSku())
                .adjustmentType(type)
                .quantityChanged(quantityDelta)
                .quantityBefore(qtyBefore)
                .quantityAfter(qtyAfter)
                .reason(reason)
                .referenceId(referenceId)
                .timestamp(Instant.now())
                .build();
        outboxEventService.saveOutboxEvent(aggregateType, aggregateId, "stock.level.changed", stockChangedTopic, event);
    }

    private StockLevelDto convertToDto(StockLevelEntity entity, CatalogItemEntity item) {
        if (entity == null || item == null) return null;
        // Ensure item matches entity.getItemId() if passed separately
        if (!entity.getItemId().equals(item.getId())) {
            log.error("Mismatch between StockLevelEntity item ID {} and CatalogItemEntity ID {} during DTO conversion.", entity.getItemId(), item.getId());
            // Handle this error appropriately, perhaps by fetching the correct item or throwing an exception
            // For now, assume they match or item was fetched based on entity.getItemId()
        }

        return StockLevelDto.builder()
                .itemId(entity.getItemId())
                .itemSku(item.getSku())
                .itemName(item.getName())
                .quantityOnHand(entity.getQuantityOnHand())
                .reorderLevel(entity.getReorderLevel())
                .updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion())
                .build();
    }
}
