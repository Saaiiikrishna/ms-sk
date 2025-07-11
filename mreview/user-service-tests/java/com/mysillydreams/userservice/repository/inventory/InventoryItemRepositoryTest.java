package com.mysillydreams.userservice.repository.inventory;

import com.mysillydreams.userservice.config.UserIntegrationTestBase;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.inventory.InventoryItem;
import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import com.mysillydreams.userservice.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ContextConfiguration(initializers = UserIntegrationTestBase.Initializer.class)
public class InventoryItemRepositoryTest {

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private InventoryProfileRepository inventoryProfileRepository;

    @Autowired
    private UserRepository userRepository;

    private UserEntity testUser;
    private InventoryProfile testInventoryProfile;

    @BeforeEach
    void setUp() {
        inventoryItemRepository.deleteAll();
        inventoryProfileRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new UserEntity();
        testUser.setReferenceId("item-user-" + UUID.randomUUID());
        testUser.setEmail(testUser.getReferenceId() + "@example.com");
        testUser = userRepository.saveAndFlush(testUser);

        testInventoryProfile = new InventoryProfile();
        testInventoryProfile.setUser(testUser);
        testInventoryProfile = inventoryProfileRepository.saveAndFlush(testInventoryProfile);
    }

    @AfterEach
    void tearDown() {
        inventoryItemRepository.deleteAll();
        inventoryProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    private InventoryItem createAndSaveItem(String sku, String name, int quantity, InventoryProfile owner) {
        InventoryItem item = new InventoryItem();
        item.setOwner(owner);
        item.setSku(sku);
        item.setName(name);
        item.setQuantityOnHand(quantity);
        item.setReorderLevel(5);
        return inventoryItemRepository.saveAndFlush(item);
    }

    @Test
    void saveAndFindById_shouldPersistAndRetrieveItem() {
        InventoryItem item = createAndSaveItem("SKU001", "Test Item 1", 100, testInventoryProfile);

        Optional<InventoryItem> foundItemOpt = inventoryItemRepository.findById(item.getId());

        assertThat(foundItemOpt).isPresent();
        InventoryItem foundItem = foundItemOpt.get();
        assertThat(foundItem.getOwner().getId()).isEqualTo(testInventoryProfile.getId());
        assertThat(foundItem.getSku()).isEqualTo("SKU001");
        assertThat(foundItem.getName()).isEqualTo("Test Item 1");
        assertThat(foundItem.getQuantityOnHand()).isEqualTo(100);
        assertThat(foundItem.getCreatedAt()).isNotNull();
        assertThat(foundItem.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByOwner_returnsItemsForCorrectOwner() {
        createAndSaveItem("SKU002", "Item A for Owner 1", 10, testInventoryProfile);
        createAndSaveItem("SKU003", "Item B for Owner 1", 20, testInventoryProfile);

        // Create another owner and item to ensure filtering
        UserEntity otherUser = userRepository.save(new UserEntity() {{
            setReferenceId("other-item-user-" + UUID.randomUUID());
            setEmail(getReferenceId() + "@example.com");
        }});
        InventoryProfile otherProfile = inventoryProfileRepository.save(new InventoryProfile() {{ setUser(otherUser); }});
        createAndSaveItem("SKU004", "Item C for Owner 2", 30, otherProfile);

        List<InventoryItem> itemsForTestProfile = inventoryItemRepository.findByOwner(testInventoryProfile);

        assertThat(itemsForTestProfile).hasSize(2);
        assertThat(itemsForTestProfile).extracting(InventoryItem::getSku).containsExactlyInAnyOrder("SKU002", "SKU003");
    }

    @Test
    void findByOwnerId_returnsItemsForCorrectOwnerId() {
        createAndSaveItem("SKU005", "Item D for Owner 1", 15, testInventoryProfile);
        List<InventoryItem> items = inventoryItemRepository.findByOwnerId(testInventoryProfile.getId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSku()).isEqualTo("SKU005");
    }


    @Test
    void findBySku_whenSkuExists_returnsItem() {
        String targetSku = "UNIQUE_SKU_001";
        createAndSaveItem(targetSku, "Unique SKU Item", 50, testInventoryProfile);
        createAndSaveItem("OTHER_SKU_002", "Another Item", 60, testInventoryProfile);

        Optional<InventoryItem> foundItemOpt = inventoryItemRepository.findBySku(targetSku);

        assertThat(foundItemOpt).isPresent();
        assertThat(foundItemOpt.get().getName()).isEqualTo("Unique SKU Item");
    }

    @Test
    void findBySku_whenSkuDoesNotExist_returnsEmpty() {
        Optional<InventoryItem> foundItemOpt = inventoryItemRepository.findBySku("NON_EXISTENT_SKU");
        assertThat(foundItemOpt).isNotPresent();
    }

    @Test
    void ensureUniqueSkuConstraint() {
        createAndSaveItem("SKU_CONSTRAINT_TEST", "Item For Constraint Test", 10, testInventoryProfile);

        InventoryItem duplicateSkuItem = new InventoryItem();
        duplicateSkuItem.setOwner(testInventoryProfile);
        duplicateSkuItem.setSku("SKU_CONSTRAINT_TEST"); // Same SKU
        duplicateSkuItem.setName("Duplicate SKU Item");
        duplicateSkuItem.setQuantityOnHand(5);
        duplicateSkuItem.setReorderLevel(1);

        assertThrows(DataIntegrityViolationException.class, () -> {
            inventoryItemRepository.saveAndFlush(duplicateSkuItem);
        });
    }

    @Test
    void findByOwnerAndSku_whenExists_returnsItem() {
        String skuToFind = "OWNER_SKU_123";
        createAndSaveItem(skuToFind, "Item for Owner/SKU test", 25, testInventoryProfile);

        Optional<InventoryItem> foundOpt = inventoryItemRepository.findByOwnerAndSku(testInventoryProfile, skuToFind);
        assertThat(foundOpt).isPresent();
        assertThat(foundOpt.get().getName()).isEqualTo("Item for Owner/SKU test");
    }

    @Test
    void findByOwnerAndSku_whenSkuExistsForDifferentOwner_returnsEmpty() {
        String skuToFind = "OWNER_SKU_456";
        createAndSaveItem(skuToFind, "Item for Owner/SKU test", 25, testInventoryProfile); // Belongs to testInventoryProfile

        UserEntity anotherUser = userRepository.save(new UserEntity() {{
            setReferenceId("another-user-" + UUID.randomUUID());
            setEmail(getReferenceId() + "@example.com");
        }});
        InventoryProfile anotherProfile = inventoryProfileRepository.save(new InventoryProfile() {{ setUser(anotherUser); }});

        Optional<InventoryItem> foundOpt = inventoryItemRepository.findByOwnerAndSku(anotherProfile, skuToFind);
        assertThat(foundOpt).isNotPresent();
    }
}
