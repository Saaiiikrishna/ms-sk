package com.mysillydreams.userservice.repository.inventory;

import com.mysillydreams.userservice.config.UserIntegrationTestBase;
import com.mysillydreams.userservice.domain.UserEntity;
import com.mysillydreams.userservice.domain.inventory.InventoryItem;
import com.mysillydreams.userservice.domain.inventory.InventoryProfile;
import com.mysillydreams.userservice.domain.inventory.StockTransaction;
import com.mysillydreams.userservice.domain.inventory.TransactionType;
import com.mysillydreams.userservice.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(initializers = UserIntegrationTestBase.Initializer.class)
public class StockTransactionRepositoryTest {

    @Autowired
    private StockTransactionRepository stockTransactionRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private InventoryProfileRepository inventoryProfileRepository;

    @Autowired
    private UserRepository userRepository;

    private UserEntity testUser;
    private InventoryProfile testInventoryProfile;
    private InventoryItem testItem1;
    private InventoryItem testItem2;

    @BeforeEach
    void setUp() {
        stockTransactionRepository.deleteAll();
        inventoryItemRepository.deleteAll();
        inventoryProfileRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new UserEntity();
        testUser.setReferenceId("tx-user-" + UUID.randomUUID());
        testUser.setEmail(testUser.getReferenceId() + "@example.com");
        testUser = userRepository.saveAndFlush(testUser);

        testInventoryProfile = new InventoryProfile();
        testInventoryProfile.setUser(testUser);
        testInventoryProfile = inventoryProfileRepository.saveAndFlush(testInventoryProfile);

        testItem1 = new InventoryItem();
        testItem1.setOwner(testInventoryProfile);
        testItem1.setSku("TX_ITEM_001");
        testItem1.setName("Transaction Item 1");
        testItem1 = inventoryItemRepository.saveAndFlush(testItem1);

        testItem2 = new InventoryItem();
        testItem2.setOwner(testInventoryProfile);
        testItem2.setSku("TX_ITEM_002");
        testItem2.setName("Transaction Item 2");
        testItem2 = inventoryItemRepository.saveAndFlush(testItem2);
    }

    @AfterEach
    void tearDown() {
        stockTransactionRepository.deleteAll();
        inventoryItemRepository.deleteAll();
        inventoryProfileRepository.deleteAll();
        userRepository.deleteAll();
    }


    private StockTransaction createAndSaveTransaction(InventoryItem item, TransactionType type, int quantity, Instant timestamp) {
        StockTransaction tx = new StockTransaction();
        tx.setItem(item);
        tx.setType(type);
        tx.setQuantity(quantity);
        if (timestamp != null) { // Allow null to test auto-generation by @CreationTimestamp
             // Forcing timestamp for tests requires a bit more work or direct field access if not using @CreationTimestamp for 'timestamp'
             // The current entity uses @CreationTimestamp for 'timestamp', so it's set on persist.
             // To test queries with specific timestamps, we'd need to persist and then potentially update, or use a different field.
             // For now, let's assume @CreationTimestamp is fine and test queries based on that.
             // If direct control over 'timestamp' is needed, remove @CreationTimestamp and set manually.
             // For this test, let's assume we can save and then check the auto-generated timestamp.
        }
        return stockTransactionRepository.saveAndFlush(tx);
    }

    // Helper to create transactions with slight time differences for sorting tests
    private StockTransaction createAndSaveTransactionWithDelay(InventoryItem item, TransactionType type, int quantity, long millisDelay) throws InterruptedException {
        if (millisDelay > 0) Thread.sleep(millisDelay); // Crude way to ensure different timestamps
        StockTransaction tx = new StockTransaction();
        tx.setItem(item);
        tx.setType(type);
        tx.setQuantity(quantity);
        return stockTransactionRepository.saveAndFlush(tx);
    }


    @Test
    void saveAndFindById_shouldPersistAndRetrieveTransaction() {
        StockTransaction tx = createAndSaveTransaction(testItem1, TransactionType.RECEIVE, 10, null);

        StockTransaction foundTx = stockTransactionRepository.findById(tx.getId()).orElse(null);

        assertThat(foundTx).isNotNull();
        assertThat(foundTx.getItem().getId()).isEqualTo(testItem1.getId());
        assertThat(foundTx.getType()).isEqualTo(TransactionType.RECEIVE);
        assertThat(foundTx.getQuantity()).isEqualTo(10);
        assertThat(foundTx.getTimestamp()).isNotNull(); // Set by @CreationTimestamp
    }

    @Test
    void findByItem_returnsSortedTransactions() throws InterruptedException {
        StockTransaction tx1 = createAndSaveTransactionWithDelay(testItem1, TransactionType.RECEIVE, 10, 0);
        StockTransaction tx2 = createAndSaveTransactionWithDelay(testItem1, TransactionType.ISSUE, 5, 10); // 10ms later
        StockTransaction tx3 = createAndSaveTransactionWithDelay(testItem1, TransactionType.ADJUSTMENT, 2, 10); // 10ms later

        // Create transaction for another item to ensure filtering
        createAndSaveTransactionWithDelay(testItem2, TransactionType.RECEIVE, 100, 0);

        List<StockTransaction> item1TxsDesc = stockTransactionRepository.findByItem(testItem1, Sort.by(Sort.Direction.DESC, "timestamp"));
        List<StockTransaction> item1TxsAsc = stockTransactionRepository.findByItem(testItem1, Sort.by(Sort.Direction.ASC, "timestamp"));

        assertThat(item1TxsDesc).hasSize(3);
        assertThat(item1TxsDesc).extracting(StockTransaction::getId).containsExactly(tx3.getId(), tx2.getId(), tx1.getId());

        assertThat(item1TxsAsc).hasSize(3);
        assertThat(item1TxsAsc).extracting(StockTransaction::getId).containsExactly(tx1.getId(), tx2.getId(), tx3.getId());
    }

    @Test
    void findByItemId_returnsSortedTransactions() throws InterruptedException {
        StockTransaction tx1 = createAndSaveTransactionWithDelay(testItem1, TransactionType.RECEIVE, 10, 0);
        createAndSaveTransactionWithDelay(testItem1, TransactionType.ISSUE, 5, 10);

        List<StockTransaction> item1Txs = stockTransactionRepository.findByItemId(testItem1.getId(), Sort.by("timestamp"));
        assertThat(item1Txs).hasSize(2);
        assertThat(item1Txs.get(0).getId()).isEqualTo(tx1.getId());
    }


    @Test
    void findByItemAndType_returnsMatchingTransactions() throws InterruptedException {
        createAndSaveTransactionWithDelay(testItem1, TransactionType.RECEIVE, 10, 0);
        StockTransaction issueTx1 = createAndSaveTransactionWithDelay(testItem1, TransactionType.ISSUE, 3, 10);
        StockTransaction issueTx2 = createAndSaveTransactionWithDelay(testItem1, TransactionType.ISSUE, 2, 10);
        createAndSaveTransactionWithDelay(testItem1, TransactionType.ADJUSTMENT, 1, 10);

        List<StockTransaction> issueTransactions = stockTransactionRepository.findByItemAndType(testItem1, TransactionType.ISSUE, Sort.by("timestamp"));

        assertThat(issueTransactions).hasSize(2);
        assertThat(issueTransactions).extracting(StockTransaction::getId).containsExactly(issueTx1.getId(), issueTx2.getId());
    }

    @Test
    void findByItemAndTimestampBetween_returnsCorrectTransactions() throws InterruptedException {
        Instant t0 = Instant.now();
        StockTransaction tx1 = createAndSaveTransactionWithDelay(testItem1, TransactionType.RECEIVE, 10, 10); // t0 + ~10ms
        Instant t1 = tx1.getTimestamp();

        StockTransaction tx2 = createAndSaveTransactionWithDelay(testItem1, TransactionType.ISSUE, 2, 10);   // t1 + ~10ms
        Instant t2 = tx2.getTimestamp();

        StockTransaction tx3 = createAndSaveTransactionWithDelay(testItem1, TransactionType.RECEIVE, 8, 10);  // t2 + ~10ms
        Instant t3 = tx3.getTimestamp();

        createAndSaveTransactionWithDelay(testItem1, TransactionType.ADJUSTMENT, 1, 10); // t3 + ~10ms (outside range)


        // Test range [t1, t3) - should include tx1, tx2, but not tx3 if t3 is exclusive
        List<StockTransaction> txsInRange = stockTransactionRepository.findByItemAndTimestampBetween(
                testItem1, t1, t3, Sort.by("timestamp") // t3 is exclusive in "Between" for Instants typically
        );

        assertThat(txsInRange).hasSize(2); // tx1 and tx2
        assertThat(txsInRange).extracting(StockTransaction::getId).containsExactly(tx1.getId(), tx2.getId());

        // Test range including t3 by going slightly after
        List<StockTransaction> txsIncludingT3 = stockTransactionRepository.findByItemAndTimestampBetween(
                testItem1, t1, t3.plus(1, ChronoUnit.MILLIS), Sort.by("timestamp")
        );
        assertThat(txsIncludingT3).hasSize(3);
        assertThat(txsIncludingT3).extracting(StockTransaction::getId).containsExactly(tx1.getId(), tx2.getId(), tx3.getId());
    }
}
