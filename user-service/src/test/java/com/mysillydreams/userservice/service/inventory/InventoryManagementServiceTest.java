package com.mysillydreams.userservice.service.inventory;

import com.mysillydreams.userservice.domain.inventory.InventoryItem;
import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import com.mysillydreams.userservice.domain.inventory.StockTransaction;
import com.mysillydreams.userservice.domain.inventory.TransactionType;
import com.mysillydreams.userservice.dto.inventory.InventoryItemDto;
import com.mysillydreams.userservice.dto.inventory.StockAdjustmentRequest;
import com.mysillydreams.userservice.repository.inventory.InventoryItemRepository;
import com.mysillydreams.userservice.repository.inventory.InventoryProfileRepository;
import com.mysillydreams.userservice.repository.inventory.StockTransactionRepository;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;


import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryManagementServiceTest {

    @Mock
    private InventoryItemRepository mockItemRepository;
    @Mock
    private StockTransactionRepository mockTransactionRepository;
    @Mock
    private InventoryProfileRepository mockInventoryProfileRepository;
    @Mock
    private InventoryKafkaClient mockKafkaClient;

    @InjectMocks
    private InventoryManagementService inventoryManagementService;

    private UUID testProfileId;
    private InventoryProfile testProfile;
    private InventoryItemDto testItemDto;
    private InventoryItem testItem;

    @BeforeEach
    void setUp() {
        testProfileId = UUID.randomUUID();
        testProfile = new InventoryProfile(testProfileId); // Constructor that sets ID

        testItemDto = new InventoryItemDto();
        testItemDto.setSku("SKU001");
        testItemDto.setName("Test Item Alpha");
        testItemDto.setDescription("Description for Alpha");
        testItemDto.setQuantityOnHand(0); // Initial quantity for DTO
        testItemDto.setReorderLevel(10);

        testItem = new InventoryItem();
        testItem.setId(UUID.randomUUID());
        testItem.setOwner(testProfile);
        testItem.setSku(testItemDto.getSku());
        testItem.setName(testItemDto.getName());
        testItem.setQuantityOnHand(50); // Initial quantity for existing item
        testItem.setReorderLevel(testItemDto.getReorderLevel());
    }

    @Test
    void addItem_success() {
        when(mockInventoryProfileRepository.findById(testProfileId)).thenReturn(Optional.of(testProfile));
        when(mockItemRepository.findBySku(testItemDto.getSku())).thenReturn(Optional.empty());
        when(mockItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> {
            InventoryItem item = inv.getArgument(0);
            item.setId(UUID.randomUUID()); // Simulate ID generation
            return item;
        });
        doNothing().when(mockKafkaClient).publishItemCreated(any(InventoryItem.class));

        InventoryItemDto resultDto = inventoryManagementService.addItem(testProfileId, testItemDto);

        assertNotNull(resultDto);
        assertEquals(testItemDto.getSku(), resultDto.getSku());
        assertEquals(testItemDto.getName(), resultDto.getName());
        assertEquals(0, resultDto.getQuantityOnHand()); // As per DTO default or explicit set in service from DTO

        ArgumentCaptor<InventoryItem> itemCaptor = ArgumentCaptor.forClass(InventoryItem.class);
        verify(mockItemRepository).save(itemCaptor.capture());
        assertEquals(testProfile, itemCaptor.getValue().getOwner());

        verify(mockKafkaClient).publishItemCreated(itemCaptor.getValue());
    }

    @Test
    void addItem_profileNotFound_throwsEntityNotFoundException() {
        when(mockInventoryProfileRepository.findById(testProfileId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> {
            inventoryManagementService.addItem(testProfileId, testItemDto);
        });
    }

    @Test
    void addItem_skuAlreadyExists_throwsIllegalArgumentException() {
        when(mockInventoryProfileRepository.findById(testProfileId)).thenReturn(Optional.of(testProfile));
        when(mockItemRepository.findBySku(testItemDto.getSku())).thenReturn(Optional.of(new InventoryItem())); // SKU exists

        assertThrows(IllegalArgumentException.class, () -> {
            inventoryManagementService.addItem(testProfileId, testItemDto);
        });
    }

    @Test
    void adjustStock_receive_success() {
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setType(TransactionType.RECEIVE);
        request.setQuantity(10);

        when(mockItemRepository.findById(testItem.getId())).thenReturn(Optional.of(testItem));
        when(mockItemRepository.save(any(InventoryItem.class))).thenReturn(testItem); // Returns updated item
        when(mockTransactionRepository.save(any(StockTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(mockKafkaClient).publishStockAdjusted(any(InventoryItem.class), any(StockTransaction.class));

        int initialQty = testItem.getQuantityOnHand();
        InventoryItemDto resultDto = inventoryManagementService.adjustStock(testItem.getId(), request);

        assertEquals(initialQty + 10, resultDto.getQuantityOnHand());
        assertEquals(initialQty + 10, testItem.getQuantityOnHand()); // Verify entity was updated

        ArgumentCaptor<StockTransaction> txCaptor = ArgumentCaptor.forClass(StockTransaction.class);
        verify(mockTransactionRepository).save(txCaptor.capture());
        assertEquals(TransactionType.RECEIVE, txCaptor.getValue().getType());
        assertEquals(10, txCaptor.getValue().getQuantity());

        verify(mockKafkaClient).publishStockAdjusted(eq(testItem), txCaptor.capture());
    }

    @Test
    void adjustStock_issue_success() {
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setType(TransactionType.ISSUE);
        request.setQuantity(5); // Issue 5 from 50

        when(mockItemRepository.findById(testItem.getId())).thenReturn(Optional.of(testItem));
        when(mockItemRepository.save(any(InventoryItem.class))).thenReturn(testItem);
        when(mockTransactionRepository.save(any(StockTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        int initialQty = testItem.getQuantityOnHand();
        InventoryItemDto resultDto = inventoryManagementService.adjustStock(testItem.getId(), request);

        assertEquals(initialQty - 5, resultDto.getQuantityOnHand());
        verify(mockKafkaClient).publishStockAdjusted(any(InventoryItem.class), any(StockTransaction.class));
    }

    @Test
    void adjustStock_issue_insufficientStock_throwsIllegalArgumentException() {
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setType(TransactionType.ISSUE);
        request.setQuantity(100); // Issue 100 from 50

        when(mockItemRepository.findById(testItem.getId())).thenReturn(Optional.of(testItem));

        assertThrows(IllegalArgumentException.class, () -> {
            inventoryManagementService.adjustStock(testItem.getId(), request);
        });
        verify(mockItemRepository, never()).save(any());
        verify(mockTransactionRepository, never()).save(any());
        verifyNoInteractions(mockKafkaClient);
    }

    @Test
    void adjustStock_adjustment_reducesStock() { // Based on current scaffold logic for ADJUSTMENT
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setType(TransactionType.ADJUSTMENT);
        request.setQuantity(3);

        when(mockItemRepository.findById(testItem.getId())).thenReturn(Optional.of(testItem));
        when(mockItemRepository.save(any(InventoryItem.class))).thenReturn(testItem);
        when(mockTransactionRepository.save(any(StockTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        int initialQty = testItem.getQuantityOnHand(); // 50
        InventoryItemDto resultDto = inventoryManagementService.adjustStock(testItem.getId(), request);

        assertEquals(initialQty - 3, resultDto.getQuantityOnHand()); // 50 - 3 = 47
        verify(mockKafkaClient).publishStockAdjusted(any(InventoryItem.class), any(StockTransaction.class));
    }


    @Test
    void adjustStock_itemNotFound_throwsEntityNotFoundException() {
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setType(TransactionType.RECEIVE);
        request.setQuantity(10);
        UUID nonExistentItemId = UUID.randomUUID();

        when(mockItemRepository.findById(nonExistentItemId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            inventoryManagementService.adjustStock(nonExistentItemId, request);
        });
    }

    @Test
    void listItemsByProfileId_success() {
        when(mockInventoryProfileRepository.existsById(testProfileId)).thenReturn(true);
        when(mockItemRepository.findByOwnerId(testProfileId)).thenReturn(List.of(testItem));

        List<InventoryItemDto> resultList = inventoryManagementService.listItemsByProfileId(testProfileId);

        assertNotNull(resultList);
        assertEquals(1, resultList.size());
        assertEquals(testItem.getSku(), resultList.get(0).getSku());
    }

    @Test
    void listItemsByProfileId_profileNotFound_throwsEntityNotFoundException() {
        when(mockInventoryProfileRepository.existsById(testProfileId)).thenReturn(false);
        assertThrows(EntityNotFoundException.class, () -> {
            inventoryManagementService.listItemsByProfileId(testProfileId);
        });
    }

    @Test
    void getItemById_success() {
        when(mockItemRepository.findById(testItem.getId())).thenReturn(Optional.of(testItem));
        InventoryItemDto result = inventoryManagementService.getItemById(testItem.getId());
        assertNotNull(result);
        assertEquals(testItem.getSku(), result.getSku());
    }

    @Test
    void getItemById_notFound_throwsEntityNotFoundException() {
        UUID randomId = UUID.randomUUID();
        when(mockItemRepository.findById(randomId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> {
            inventoryManagementService.getItemById(randomId);
        });
    }

    @Test
    void updateItem_success() {
        InventoryItemDto updateDto = new InventoryItemDto();
        updateDto.setName("Updated Item Name");
        updateDto.setDescription("New Description");
        updateDto.setReorderLevel(5);

        when(mockItemRepository.findById(testItem.getId())).thenReturn(Optional.of(testItem));
        when(mockItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryItemDto result = inventoryManagementService.updateItem(testItem.getId(), updateDto);

        assertEquals("Updated Item Name", result.getName());
        assertEquals("New Description", result.getDescription());
        assertEquals(5, result.getReorderLevel());
        // SKU and QuantityOnHand should remain unchanged by this method
        assertEquals(testItem.getSku(), result.getSku());
        assertEquals(testItem.getQuantityOnHand(), result.getQuantityOnHand());
    }

    @Test
    void updateItem_notFound_throwsEntityNotFoundException() {
        UUID randomId = UUID.randomUUID();
        InventoryItemDto updateDto = new InventoryItemDto();
        updateDto.setName("Update");
        when(mockItemRepository.findById(randomId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> {
            inventoryManagementService.updateItem(randomId, updateDto);
        });
    }

    @Test
    void listTransactionsForItem_success() {
        StockTransaction tx1 = new StockTransaction();
        StockTransaction tx2 = new StockTransaction();
        when(mockItemRepository.existsById(testItem.getId())).thenReturn(true);
        when(mockTransactionRepository.findByItemId(eq(testItem.getId()), any(Sort.class))).thenReturn(List.of(tx1, tx2));

        List<StockTransaction> result = inventoryManagementService.listTransactionsForItem(testItem.getId());

        assertEquals(2, result.size());
    }

    @Test
    void listTransactionsForItem_itemNotFound_throwsEntityNotFoundException() {
        UUID randomId = UUID.randomUUID();
        when(mockItemRepository.existsById(randomId)).thenReturn(false);
        assertThrows(EntityNotFoundException.class, () -> {
            inventoryManagementService.listTransactionsForItem(randomId);
        });
    }
}
