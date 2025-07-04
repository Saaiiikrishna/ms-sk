package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.CategoryEntity;
import com.mysillydreams.catalogservice.domain.model.PriceHistoryEntity;
import com.mysillydreams.catalogservice.domain.model.StockLevelEntity; // For creating initial stock
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.domain.repository.CategoryRepository;
import com.mysillydreams.catalogservice.domain.repository.PriceHistoryRepository;
import com.mysillydreams.catalogservice.domain.repository.StockLevelRepository; // For creating initial stock
import com.mysillydreams.catalogservice.dto.CatalogItemDto;
import com.mysillydreams.catalogservice.dto.CreateCatalogItemRequest;
import com.mysillydreams.catalogservice.exception.DuplicateResourceException;
import com.mysillydreams.catalogservice.exception.InvalidRequestException;
import com.mysillydreams.catalogservice.exception.ResourceNotFoundException;
import com.mysillydreams.catalogservice.kafka.event.CatalogItemEvent;
import com.mysillydreams.catalogservice.kafka.event.PriceUpdatedEvent;
import com.mysillydreams.catalogservice.kafka.producer.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemService {

    private final CatalogItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final StockLevelRepository stockLevelRepository; // For initial stock record for products
    private final KafkaProducerService kafkaProducerService;

    @Value("${app.kafka.topic.item-created}")
    private String itemCreatedTopic;
    @Value("${app.kafka.topic.item-updated}")
    private String itemUpdatedTopic;
    @Value("${app.kafka.topic.item-deleted}")
    private String itemDeletedTopic;
    @Value("${app.kafka.topic.price-updated}")
    private String priceUpdatedTopic;

    @Transactional
    public CatalogItemDto createItem(CreateCatalogItemRequest request) {
        log.info("Creating catalog item with SKU: {}", request.getSku());

        itemRepository.findBySku(request.getSku()).ifPresent(item -> {
            throw new DuplicateResourceException("CatalogItem", "SKU", request.getSku());
        });

        CategoryEntity category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));

        if (category.getType() != request.getItemType()) {
            throw new InvalidRequestException(String.format(
                    "Item type '%s' does not match category type '%s'.",
                    request.getItemType(), category.getType()
            ));
        }

        CatalogItemEntity item = CatalogItemEntity.builder()
                .category(category)
                .sku(request.getSku())
                .name(request.getName())
                .description(request.getDescription())
                .itemType(request.getItemType())
                .basePrice(request.getBasePrice())
                .metadata(request.getMetadata())
                .active(request.isActive())
                .build();

        CatalogItemEntity savedItem = itemRepository.save(item);

        // Create initial price history record
        PriceHistoryEntity initialPrice = PriceHistoryEntity.builder()
                .catalogItem(savedItem)
                .price(savedItem.getBasePrice())
                .effectiveFrom(savedItem.getCreatedAt()) // Price effective from creation time
                .build();
        priceHistoryRepository.save(initialPrice);

        // If it's a PRODUCT, create an initial stock level record (defaulting to 0 or a specified initial quantity)
        if (savedItem.getItemType() == com.mysillydreams.catalogservice.domain.model.ItemType.PRODUCT) {
            StockLevelEntity stock = StockLevelEntity.builder()
                    //.itemId(savedItem.getId()) // Not needed due to @MapsId and setting catalogItem
                    .catalogItem(savedItem)
                    .quantityOnHand(0) // Default initial stock to 0
                    .reorderLevel(0)   // Default reorder level
                    .build();
            stockLevelRepository.save(stock);
        }


        CatalogItemDto itemDto = convertToDto(savedItem);
        publishItemEvent(itemCreatedTopic, "catalog.item.created", savedItem);
        // PriceUpdatedEvent is not strictly necessary here as it's the initial price,
        // but if consumers expect it for all price settings, it could be published.
        // For now, ItemEvent includes the basePrice.

        log.info("Catalog item created successfully with ID: {}", savedItem.getId());
        return itemDto;
    }

    @Transactional(readOnly = true)
    public boolean itemExists(UUID itemId) {
        return itemRepository.existsById(itemId);
    }

    @Transactional(readOnly = true)
    public CatalogItemDto getItemById(UUID itemId) {
        log.debug("Fetching catalog item by ID: {}", itemId);
        CatalogItemEntity item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CatalogItem", "id", itemId));
        return convertToDto(item);
    }

    @Transactional(readOnly = true)
    public CatalogItemDto getItemBySku(String sku) {
        log.debug("Fetching catalog item by SKU: {}", sku);
        CatalogItemEntity item = itemRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("CatalogItem", "sku", sku));
        return convertToDto(item);
    }

    @Transactional(readOnly = true)
    public Page<CatalogItemDto> getItemsByCategoryId(UUID categoryId, Pageable pageable) {
        log.debug("Fetching items for category ID: {}", categoryId);
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category", "id", categoryId);
        }
        Page<CatalogItemEntity> items = itemRepository.findByCategoryId(categoryId, pageable);
        return items.map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public Page<CatalogItemDto> getAllItems(Pageable pageable) {
        log.debug("Fetching all items with pagination");
        Page<CatalogItemEntity> items = itemRepository.findAll(pageable);
        return items.map(this::convertToDto);
    }


    @Transactional
    public CatalogItemDto updateItem(UUID itemId, CreateCatalogItemRequest request) {
        log.info("Updating catalog item with ID: {}", itemId);
        CatalogItemEntity item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CatalogItem", "id", itemId));

        // SKU change validation (usually SKU is immutable, but if allowed):
        if (!item.getSku().equals(request.getSku())) {
            itemRepository.findBySku(request.getSku()).ifPresent(existingItem -> {
                if (!existingItem.getId().equals(itemId)) { // If SKU belongs to another item
                    throw new DuplicateResourceException("CatalogItem", "SKU", request.getSku());
                }
            });
            item.setSku(request.getSku());
        }

        // Category change
        if (!item.getCategory().getId().equals(request.getCategoryId())) {
            CategoryEntity newCategory = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
            if (newCategory.getType() != request.getItemType()) { // Also check if item type is changing
                 throw new InvalidRequestException(String.format(
                    "New item type '%s' must match new category type '%s'.",
                    request.getItemType(), newCategory.getType()
            ));
            }
            item.setCategory(newCategory);
        }

        // ItemType change validation with category
        if (item.getItemType() != request.getItemType() && item.getCategory().getType() != request.getItemType()) {
             throw new InvalidRequestException(String.format(
                    "New item type '%s' must match current category type '%s'. Change category first or pick correct item type.",
                    request.getItemType(), item.getCategory().getType()
            ));
        }
        item.setItemType(request.getItemType());


        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setMetadata(request.getMetadata());
        item.setActive(request.isActive());

        // Price change handling
        if (item.getBasePrice().compareTo(request.getBasePrice()) != 0) {
            BigDecimal oldPrice = item.getBasePrice();
            item.setBasePrice(request.getBasePrice());
            // Create a new price history record
            PriceHistoryEntity priceChange = PriceHistoryEntity.builder()
                    .catalogItem(item)
                    .price(request.getBasePrice())
                    .effectiveFrom(Instant.now()) // Price change effective immediately
                    .build();
            priceHistoryRepository.save(priceChange);

            // Publish price updated event
            publishPriceUpdatedEvent(item, oldPrice, request.getBasePrice());
        }

        CatalogItemEntity updatedItem = itemRepository.save(item);
        CatalogItemDto itemDto = convertToDto(updatedItem);
        publishItemEvent(itemUpdatedTopic, "catalog.item.updated", updatedItem);
        log.info("Catalog item updated successfully with ID: {}", updatedItem.getId());
        return itemDto;
    }

    @Transactional
    public void deleteItem(UUID itemId) {
        log.info("Deleting catalog item with ID: {}", itemId);
        CatalogItemEntity item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CatalogItem", "id", itemId));

        // Business rule: Cannot delete item if it's part of active carts or has pending orders.
        // This check would typically involve communicating with CartService/OrderService or checking local denormalized data.
        // For now, we'll keep it simple. A more robust solution might involve soft-delete or archival.
        // Consider if stock records, price history, bulk rules should be cascade deleted or handled.
        // JPA orphanRemoval on CatalogItem for these related entities might be an option, or manual deletion.
        // For now, assuming related data might be kept for historical purposes or needs explicit cleanup.
        // If StockLevel has a FK constraint without ON DELETE CASCADE, it would prevent item deletion if stock exists.
        // Let's assume for now that related entities like PriceHistory should be kept.
        // StockLevelEntity for a product *should* be deleted if the product is deleted.
        // BulkPricingRuleEntity for an item *should* be deleted if the item is deleted.

        if (item.getItemType() == com.mysillydreams.catalogservice.domain.model.ItemType.PRODUCT) {
            stockLevelRepository.findByCatalogItemId(itemId).ifPresent(stockLevelRepository::delete);
        }
        // priceHistoryRepository.deleteByCatalogItemId(itemId); // If we want to delete history
        // bulkPricingRuleRepository.deleteByCatalogItemId(itemId); // If we want to delete rules


        itemRepository.delete(item);
        publishItemEvent(itemDeletedTopic, "catalog.item.deleted", item); // Send event before actual deletion or with all details
        log.info("Catalog item deleted successfully with ID: {}", itemId);
    }

    @Transactional
    public CatalogItemDto updateItemPrice(UUID itemId, BigDecimal newPrice) {
        log.info("Updating price for item ID: {} to new price: {}", itemId, newPrice);
        if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("New price must be greater than zero.");
        }

        CatalogItemEntity item = itemRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("CatalogItem", "id", itemId));

        BigDecimal oldPrice = item.getBasePrice();
        if (oldPrice.compareTo(newPrice) == 0) {
            log.info("New price is same as current base price for item ID: {}. No update performed.", itemId);
            return convertToDto(item); // Or throw exception / return different response
        }

        item.setBasePrice(newPrice);

        PriceHistoryEntity priceChange = PriceHistoryEntity.builder()
            .catalogItem(item)
            .price(newPrice)
            .effectiveFrom(Instant.now())
            .build();
        priceHistoryRepository.save(priceChange);

        CatalogItemEntity updatedItem = itemRepository.save(item); // Save item to update its basePrice field

        publishPriceUpdatedEvent(updatedItem, oldPrice, newPrice);
        publishItemEvent(itemUpdatedTopic, "catalog.item.updated", updatedItem); // Also send a general item update

        log.info("Price updated successfully for item ID: {}. Old price: {}, New price: {}", itemId, oldPrice, newPrice);
        return convertToDto(updatedItem);
    }


    private void publishItemEvent(String topic, String eventType, CatalogItemEntity item) {
        CatalogItemEvent event = CatalogItemEvent.builder()
                .eventType(eventType)
                .itemId(item.getId())
                .categoryId(item.getCategory().getId())
                .sku(item.getSku())
                .name(item.getName())
                .description(item.getDescription()) // Be mindful of event size with large descriptions
                .itemType(item.getItemType())
                .basePrice(item.getBasePrice())
                .metadata(item.getMetadata())
                .active(item.isActive())
                .timestamp(Instant.now())
                .build();
        kafkaProducerService.sendMessage(topic, item.getId().toString(), event);
    }

    private void publishPriceUpdatedEvent(CatalogItemEntity item, BigDecimal oldPrice, BigDecimal newPrice) {
        PriceUpdatedEvent event = PriceUpdatedEvent.builder()
                .itemId(item.getId())
                .sku(item.getSku())
                .oldPrice(oldPrice)
                .newPrice(newPrice)
                .timestamp(Instant.now())
                .build();
        kafkaProducerService.sendMessage(priceUpdatedTopic, item.getId().toString(), event);
    }

    private CatalogItemDto convertToDto(CatalogItemEntity entity) {
        if (entity == null) return null;
        // Basic DTO conversion. Stock/CurrentPrice might be enriched by another layer/service if needed.
        return CatalogItemDto.builder()
                .id(entity.getId())
                .categoryId(entity.getCategory().getId())
                .categoryName(entity.getCategory().getName()) // Denormalized
                .sku(entity.getSku())
                .name(entity.getName())
                .description(entity.getDescription())
                .itemType(entity.getItemType())
                .basePrice(entity.getBasePrice())
                .metadata(entity.getMetadata())
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                // quantityOnHand and currentPrice would be set by a facade/aggregator service normally
                // For now, let's try to populate quantityOnHand if it's a product
                .quantityOnHand(
                    entity.getItemType() == com.mysillydreams.catalogservice.domain.model.ItemType.PRODUCT ?
                    stockLevelRepository.findByCatalogItemId(entity.getId()).map(StockLevelEntity::getQuantityOnHand).orElse(0) : null
                )
                .currentPrice(entity.getBasePrice()) // Simplistic: currentPrice is basePrice. PricingService will handle more complex logic.
                .build();
    }
}
