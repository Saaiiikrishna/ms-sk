package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.*;
import com.mysillydreams.catalogservice.domain.repository.*;
import com.mysillydreams.catalogservice.dto.CatalogItemDto;
import com.mysillydreams.catalogservice.dto.CreateCatalogItemRequest;
import com.mysillydreams.catalogservice.exception.DuplicateResourceException;
import com.mysillydreams.catalogservice.exception.InvalidRequestException;
import com.mysillydreams.catalogservice.exception.ResourceNotFoundException;
import com.mysillydreams.catalogservice.kafka.event.CatalogItemEvent;
import com.mysillydreams.catalogservice.kafka.event.PriceUpdatedEvent;
import com.mysillydreams.catalogservice.kafka.producer.KafkaProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ItemServiceTest {

    @Mock private CatalogItemRepository itemRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private PriceHistoryRepository priceHistoryRepository;
    @Mock private StockLevelRepository stockLevelRepository;
    @Mock private KafkaProducerService kafkaProducerService;

    @InjectMocks private ItemService itemService;

    private CategoryEntity productCategory;
    private CategoryEntity serviceCategory;
    private UUID productCategoryId = UUID.randomUUID();
    private UUID serviceCategoryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(itemService, "itemCreatedTopic", "item.created");
        ReflectionTestUtils.setField(itemService, "itemUpdatedTopic", "item.updated");
        ReflectionTestUtils.setField(itemService, "itemDeletedTopic", "item.deleted");
        ReflectionTestUtils.setField(itemService, "priceUpdatedTopic", "price.updated");

        productCategory = CategoryEntity.builder().id(productCategoryId).name("Electronics").type(ItemType.PRODUCT).path("/elec/").build();
        serviceCategory = CategoryEntity.builder().id(serviceCategoryId).name("Support").type(ItemType.SERVICE).path("/support/").build();
    }

    @Test
    void createItem_product_success() {
        CreateCatalogItemRequest request = CreateCatalogItemRequest.builder()
                .categoryId(productCategoryId).sku("SKU001").name("Laptop")
                .itemType(ItemType.PRODUCT).basePrice(new BigDecimal("1200.00"))
                .build();

        when(categoryRepository.findById(productCategoryId)).thenReturn(Optional.of(productCategory));
        when(itemRepository.findBySku("SKU001")).thenReturn(Optional.empty());

        CatalogItemEntity savedItem = CatalogItemEntity.builder()
            .id(UUID.randomUUID()).category(productCategory).sku("SKU001").name("Laptop")
            .itemType(ItemType.PRODUCT).basePrice(new BigDecimal("1200.00"))
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
        when(itemRepository.save(any(CatalogItemEntity.class))).thenReturn(savedItem);
        when(priceHistoryRepository.save(any(PriceHistoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockLevelRepository.save(any(StockLevelEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        // Mock stock level for DTO conversion
        when(stockLevelRepository.findByCatalogItemId(savedItem.getId()))
            .thenReturn(Optional.of(StockLevelEntity.builder().catalogItem(savedItem).quantityOnHand(0).build()));


        CatalogItemDto result = itemService.createItem(request);

        assertThat(result).isNotNull();
        assertThat(result.getSku()).isEqualTo("SKU001");
        verify(itemRepository).save(any(CatalogItemEntity.class));
        verify(priceHistoryRepository).save(any(PriceHistoryEntity.class));
        verify(stockLevelRepository).save(argThat(sl -> sl.getCatalogItem().getId().equals(savedItem.getId()) && sl.getQuantityOnHand() == 0)); // Check initial stock
        verify(kafkaProducerService).sendMessage(eq("item.created"), eq(savedItem.getId().toString()), any(CatalogItemEvent.class));
    }

    @Test
    void createItem_service_success_noStockRecord() {
        CreateCatalogItemRequest request = CreateCatalogItemRequest.builder()
                .categoryId(serviceCategoryId).sku("SERV001").name("Basic Support")
                .itemType(ItemType.SERVICE).basePrice(new BigDecimal("50.00"))
                .build();

        when(categoryRepository.findById(serviceCategoryId)).thenReturn(Optional.of(serviceCategory));
        when(itemRepository.findBySku("SERV001")).thenReturn(Optional.empty());

        CatalogItemEntity savedItem = CatalogItemEntity.builder()
            .id(UUID.randomUUID()).category(serviceCategory).sku("SERV001").name("Basic Support")
            .itemType(ItemType.SERVICE).basePrice(new BigDecimal("50.00"))
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
        when(itemRepository.save(any(CatalogItemEntity.class))).thenReturn(savedItem);
        when(priceHistoryRepository.save(any(PriceHistoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        // No need to mock stockLevelRepository.findByCatalogItemId for service type in DTO conversion if it correctly returns null for quantity

        CatalogItemDto result = itemService.createItem(request);

        assertThat(result).isNotNull();
        assertThat(result.getSku()).isEqualTo("SERV001");
        assertThat(result.getQuantityOnHand()).isNull(); // Services should not have stock
        verify(itemRepository).save(any(CatalogItemEntity.class));
        verify(priceHistoryRepository).save(any(PriceHistoryEntity.class));
        verify(stockLevelRepository, never()).save(any(StockLevelEntity.class)); // No stock for services
        verify(kafkaProducerService).sendMessage(eq("item.created"), eq(savedItem.getId().toString()), any(CatalogItemEvent.class));
    }


    @Test
    void createItem_duplicateSku_throwsException() {
        CreateCatalogItemRequest request = CreateCatalogItemRequest.builder().categoryId(productCategoryId).sku("SKU001").name("Laptop").itemType(ItemType.PRODUCT).basePrice(BigDecimal.TEN).build();
        when(itemRepository.findBySku("SKU001")).thenReturn(Optional.of(new CatalogItemEntity()));

        assertThrows(DuplicateResourceException.class, () -> itemService.createItem(request));
    }

    @Test
    void createItem_categoryNotFound_throwsException() {
        CreateCatalogItemRequest request = CreateCatalogItemRequest.builder().categoryId(UUID.randomUUID()).sku("SKU001").name("Laptop").itemType(ItemType.PRODUCT).basePrice(BigDecimal.TEN).build();
        when(categoryRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> itemService.createItem(request));
    }

    @Test
    void createItem_itemTypeCategoryTypeMismatch_throwsException() {
        CreateCatalogItemRequest request = CreateCatalogItemRequest.builder()
                .categoryId(productCategoryId) // Product category
                .sku("SKU-MISMATCH")
                .name("Service Item in Product Category")
                .itemType(ItemType.SERVICE) // Item is a service
                .basePrice(new BigDecimal("100.00"))
                .build();

        when(categoryRepository.findById(productCategoryId)).thenReturn(Optional.of(productCategory)); // productCategory is type PRODUCT

        Exception exception = assertThrows(InvalidRequestException.class, () -> itemService.createItem(request));
        assertThat(exception.getMessage()).contains("Item type 'SERVICE' does not match category type 'PRODUCT'");
    }


    @Test
    void getItemById_found_returnsDto() {
        UUID itemId = UUID.randomUUID();
        CatalogItemEntity itemEntity = CatalogItemEntity.builder().id(itemId).sku("SKU00X").name("Found Item").category(productCategory).itemType(ItemType.PRODUCT).basePrice(BigDecimal.ONE).build();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(itemEntity));
        when(stockLevelRepository.findByCatalogItemId(itemId)).thenReturn(Optional.of(StockLevelEntity.builder().quantityOnHand(10).build()));


        CatalogItemDto result = itemService.getItemById(itemId);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Found Item");
        assertThat(result.getQuantityOnHand()).isEqualTo(10);
    }

    @Test
    void updateItem_priceChange_createsHistoryAndSendsEvents() {
        UUID itemId = UUID.randomUUID();
        CatalogItemEntity existingItem = CatalogItemEntity.builder()
                .id(itemId).sku("SKU-OLD").name("Old Item").category(productCategory)
                .itemType(ItemType.PRODUCT).basePrice(new BigDecimal("100.00"))
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        CreateCatalogItemRequest updateRequest = CreateCatalogItemRequest.builder()
                .categoryId(productCategoryId).sku("SKU-OLD").name("New Item Name") // Name change
                .itemType(ItemType.PRODUCT).basePrice(new BigDecimal("120.00")) // Price change
                .build();

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(existingItem));
        when(itemRepository.save(any(CatalogItemEntity.class))).thenAnswer(inv -> inv.getArgument(0)); // Return same entity
        when(priceHistoryRepository.save(any(PriceHistoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        // For DTO conversion after update
        when(stockLevelRepository.findByCatalogItemId(itemId)).thenReturn(Optional.of(StockLevelEntity.builder().quantityOnHand(5).build()));


        CatalogItemDto result = itemService.updateItem(itemId, updateRequest);

        assertThat(result.getName()).isEqualTo("New Item Name");
        assertThat(result.getBasePrice()).isEqualTo(new BigDecimal("120.00"));

        verify(priceHistoryRepository).save(argThat(ph -> ph.getPrice().compareTo(new BigDecimal("120.00")) == 0));

        ArgumentCaptor<PriceUpdatedEvent> priceEventCaptor = ArgumentCaptor.forClass(PriceUpdatedEvent.class);
        verify(kafkaProducerService).sendMessage(eq("price.updated"), eq(itemId.toString()), priceEventCaptor.capture());
        assertThat(priceEventCaptor.getValue().getOldPrice()).isEqualTo(new BigDecimal("100.00"));
        assertThat(priceEventCaptor.getValue().getNewPrice()).isEqualTo(new BigDecimal("120.00"));

        verify(kafkaProducerService).sendMessage(eq("item.updated"), eq(itemId.toString()), any(CatalogItemEvent.class));
    }

    @Test
    void updateItemPrice_validNewPrice_updatesBasePriceAndCreatesHistory() {
        UUID itemId = UUID.randomUUID();
        BigDecimal oldPrice = new BigDecimal("50.00");
        BigDecimal newPrice = new BigDecimal("55.00");

        CatalogItemEntity item = CatalogItemEntity.builder()
            .id(itemId).sku("PRICE-ITEM").name("Item for Price Update").category(productCategory)
            .itemType(ItemType.PRODUCT).basePrice(oldPrice)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(CatalogItemEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(priceHistoryRepository.save(any(PriceHistoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockLevelRepository.findByCatalogItemId(itemId)).thenReturn(Optional.of(StockLevelEntity.builder().quantityOnHand(0).build()));


        CatalogItemDto result = itemService.updateItemPrice(itemId, newPrice);

        assertThat(result.getBasePrice()).isEqualTo(newPrice);
        verify(itemRepository).save(argThat(savedItem -> savedItem.getBasePrice().equals(newPrice)));
        verify(priceHistoryRepository).save(argThat(ph -> ph.getPrice().equals(newPrice) && ph.getCatalogItem().getId().equals(itemId)));

        ArgumentCaptor<PriceUpdatedEvent> priceEventCaptor = ArgumentCaptor.forClass(PriceUpdatedEvent.class);
        verify(kafkaProducerService).sendMessage(eq("price.updated"), eq(itemId.toString()), priceEventCaptor.capture());
        assertThat(priceEventCaptor.getValue().getOldPrice()).isEqualTo(oldPrice);
        assertThat(priceEventCaptor.getValue().getNewPrice()).isEqualTo(newPrice);

        verify(kafkaProducerService).sendMessage(eq("item.updated"), eq(itemId.toString()), any(CatalogItemEvent.class)); // General item update also sent
    }

    @Test
    void updateItemPrice_samePrice_noActionAndReturnsCurrentDto() {
        UUID itemId = UUID.randomUUID();
        BigDecimal currentPrice = new BigDecimal("75.00");

        CatalogItemEntity item = CatalogItemEntity.builder()
            .id(itemId).sku("SAME-PRICE-ITEM").name("Item No Price Change").category(productCategory)
            .itemType(ItemType.PRODUCT).basePrice(currentPrice)
            .build();

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        // For DTO conversion
        when(stockLevelRepository.findByCatalogItemId(itemId)).thenReturn(Optional.of(StockLevelEntity.builder().quantityOnHand(0).build()));


        CatalogItemDto result = itemService.updateItemPrice(itemId, currentPrice); // Attempt to update with the same price

        assertThat(result.getBasePrice()).isEqualTo(currentPrice);
        verify(itemRepository, never()).save(any(CatalogItemEntity.class)); // Should not save item if price is same
        verify(priceHistoryRepository, never()).save(any(PriceHistoryEntity.class)); // No new history
        verify(kafkaProducerService, never()).sendMessage(eq("price.updated"), anyString(), any()); // No price event
        verify(kafkaProducerService, never()).sendMessage(eq("item.updated"), anyString(), any()); // No general item event either if only price was "changed"
    }


    @Test
    void deleteItem_product_deletesItemAndStockLevel() {
        UUID itemId = UUID.randomUUID();
        CatalogItemEntity itemEntity = CatalogItemEntity.builder().id(itemId).sku("DEL-ITEM").name("Item To Delete").category(productCategory).itemType(ItemType.PRODUCT).build();
        StockLevelEntity stockEntity = StockLevelEntity.builder().catalogItem(itemEntity).quantityOnHand(10).build();

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(itemEntity));
        when(stockLevelRepository.findByCatalogItemId(itemId)).thenReturn(Optional.of(stockEntity));

        itemService.deleteItem(itemId);

        verify(stockLevelRepository).delete(stockEntity);
        verify(itemRepository).delete(itemEntity);
        verify(kafkaProducerService).sendMessage(eq("item.deleted"), eq(itemId.toString()), any(CatalogItemEvent.class));
    }

    @Test
    void getAllItems_returnsPagedDtos() {
        UUID item1Id = UUID.randomUUID();
        CatalogItemEntity item1 = CatalogItemEntity.builder().id(item1Id).name("Item 1").sku("S1").category(productCategory).itemType(ItemType.PRODUCT).basePrice(BigDecimal.ONE).build();
        UUID item2Id = UUID.randomUUID();
        CatalogItemEntity item2 = CatalogItemEntity.builder().id(item2Id).name("Item 2").sku("S2").category(serviceCategory).itemType(ItemType.SERVICE).basePrice(BigDecimal.TEN).build();
        Pageable pageable = PageRequest.of(0, 10);
        Page<CatalogItemEntity> itemPage = new PageImpl<>(List.of(item1, item2), pageable, 2);

        when(itemRepository.findAll(pageable)).thenReturn(itemPage);
        // Mock stock for item1 for DTO conversion
        when(stockLevelRepository.findByCatalogItemId(item1Id)).thenReturn(Optional.of(StockLevelEntity.builder().quantityOnHand(5).build()));
        // No stock mock for item2 (service) needed if DTO conversion handles it

        Page<CatalogItemDto> resultPage = itemService.getAllItems(pageable);

        assertThat(resultPage.getTotalElements()).isEqualTo(2);
        assertThat(resultPage.getContent()).hasSize(2);
        assertThat(resultPage.getContent().get(0).getName()).isEqualTo("Item 1");
        assertThat(resultPage.getContent().get(0).getQuantityOnHand()).isEqualTo(5);
        assertThat(resultPage.getContent().get(1).getName()).isEqualTo("Item 2");
        assertThat(resultPage.getContent().get(1).getQuantityOnHand()).isNull();
    }
}
