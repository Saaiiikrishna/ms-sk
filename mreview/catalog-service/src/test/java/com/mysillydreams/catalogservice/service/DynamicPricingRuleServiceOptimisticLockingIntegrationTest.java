package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.model.CategoryEntity;
import com.mysillydreams.catalogservice.domain.model.DynamicPricingRuleEntity;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.domain.repository.CategoryRepository;
import com.mysillydreams.catalogservice.domain.repository.DynamicPricingRuleRepository;
import com.mysillydreams.catalogservice.dto.UpdateDynamicPricingRuleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class DynamicPricingRuleServiceOptimisticLockingIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DynamicPricingRuleServiceOptimisticLockingIntegrationTest.class);

    @Container
    private static final PostgreSQLContainer<?> postgresqlContainer =
            new PostgreSQLContainer<>("postgres:15.3-alpine")
                    .withDatabaseName("test_catalog_db_optimistic_rules")
                    .withUsername("testuser")
                    .withPassword("testpassword");

    static {
        System.setProperty("spring.datasource.url", postgresqlContainer.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgresqlContainer.getUsername());
        System.setProperty("spring.datasource.password", postgresqlContainer.getPassword());
        System.setProperty("spring.datasource.driver-class-name", "org.testcontainers.jdbc.ContainerDatabaseDriver");
        System.setProperty("spring.liquibase.enabled", "true");
        System.setProperty("spring.liquibase.change-log", "classpath:db/changelog/db.changelog-master.yaml");
    }

    @Autowired
    private DynamicPricingRuleService dynamicPricingRuleService;
    @Autowired
    private DynamicPricingRuleRepository ruleRepository;
    @Autowired
    private CatalogItemRepository itemRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private CatalogItemEntity testItem;
    private DynamicPricingRuleEntity baseRule;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);


        ruleRepository.deleteAll();
        itemRepository.deleteAll();
        categoryRepository.deleteAll();

        CategoryEntity category = categoryRepository.save(CategoryEntity.builder().name("OptLock Category").type(ItemType.PRODUCT).path("/optlock/").build());
        testItem = itemRepository.save(CatalogItemEntity.builder().category(category).sku("OPTLOCK-ITEM").name("Optimistic Lock Test Item").itemType(ItemType.PRODUCT).basePrice(BigDecimal.valueOf(200.00)).build());

        Map<String, Object> initialParams = new HashMap<>();
        initialParams.put("fixedDiscount", 5.0);
        baseRule = ruleRepository.save(DynamicPricingRuleEntity.builder()
                .catalogItem(testItem)
                .ruleType("FIXED_AMOUNT_OFF")
                .parameters(initialParams)
                .enabled(true)
                .createdBy("setup-user")
                .build());
    }

    @Test
    void concurrentRuleUpdates_shouldSucceedWithRetriesAndUpdateVersion() throws InterruptedException {
        int numberOfThreads = 5;
        int updatesPerThread = 2; // Each thread attempts two distinct updates on the same rule
        AtomicInteger totalSuccessfulServiceCalls = new AtomicInteger(0);
        AtomicInteger optimisticLockExceptionsCaughtInTest = new AtomicInteger(0); // Failures propagated to test

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads * updatesPerThread);

        long initialVersion = ruleRepository.findById(baseRule.getId()).get().getVersion();

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadIndex = i;
            executorService.submit(() -> {
                try {
                    // First update attempt by this thread
                    transactionTemplate.execute(status -> {
                        try {
                            DynamicPricingRuleEntity currentRule = ruleRepository.findById(baseRule.getId()).orElseThrow();
                            Map<String, Object> params1 = new HashMap<>(currentRule.getParameters());
                            params1.put("thread" + threadIndex + "_update", 1);

                            UpdateDynamicPricingRuleRequest request1 = UpdateDynamicPricingRuleRequest.builder()
                                    .itemId(testItem.getId())
                                    .ruleType(currentRule.getRuleType())
                                    .parameters(params1)
                                    .enabled(!currentRule.isEnabled()) // Toggle enabled
                                    .build();
                            dynamicPricingRuleService.updateRule(baseRule.getId(), request1, "thread-" + threadIndex + "-op1");
                            totalSuccessfulServiceCalls.incrementAndGet();
                        } catch (OptimisticLockingFailureException e) {
                            log.warn("Thread {} Op1: OptimisticLockingFailureException caught in test transaction wrapper (should be rare if @Retryable works)", threadIndex, e);
                            optimisticLockExceptionsCaughtInTest.incrementAndGet();
                            status.setRollbackOnly(); // Rollback this attempt
                        } catch (Exception e) {
                            log.error("Thread {} Op1: Unexpected exception during rule update", threadIndex, e);
                            status.setRollbackOnly();
                        } finally {
                            latch.countDown();
                        }
                        return null;
                    });

                    Thread.sleep(10); // Small delay to increase chance of collision for the second update

                    // Second update attempt by this thread
                    transactionTemplate.execute(status -> {
                        try {
                            DynamicPricingRuleEntity currentRule = ruleRepository.findById(baseRule.getId()).orElseThrow();
                            Map<String, Object> params2 = new HashMap<>(currentRule.getParameters());
                            params2.put("thread" + threadIndex + "_update", 2);

                            UpdateDynamicPricingRuleRequest request2 = UpdateDynamicPricingRuleRequest.builder()
                                    .itemId(testItem.getId())
                                    .ruleType(currentRule.getRuleType())
                                    .parameters(params2)
                                    .enabled(!currentRule.isEnabled()) // Toggle enabled again
                                    .build();
                            dynamicPricingRuleService.updateRule(baseRule.getId(), request2, "thread-" + threadIndex + "-op2");
                            totalSuccessfulServiceCalls.incrementAndGet();
                        } catch (OptimisticLockingFailureException e) {
                            log.warn("Thread {} Op2: OptimisticLockingFailureException caught in test transaction wrapper", threadIndex, e);
                            optimisticLockExceptionsCaughtInTest.incrementAndGet();
                            status.setRollbackOnly();
                        } catch (Exception e) {
                            log.error("Thread {} Op2: Unexpected exception during rule update", threadIndex, e);
                            status.setRollbackOnly();
                        } finally {
                            latch.countDown();
                        }
                        return null;
                    });

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Thread {} was interrupted.", threadIndex);
                } catch (Exception e) {
                    log.error("Thread {} encountered an unexpected error at a higher level.", threadIndex, e);
                    // Ensure latch is counted down if an error occurs outside the transactionTemplate scope
                    // but within the thread's main try block for both operations.
                    if(latch.getCount() > 0 && (e instanceof InterruptedException == false)) {
                        latch.countDown(); // If one op finished, other failed, may need two countdowns
                        if(latch.getCount() > 0 ) latch.countDown();
                    }
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        boolean terminated = executorService.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(completed).isTrue().withFailMessage("Latch did not count down to zero. Tasks pending: " + latch.getCount());
        assertThat(terminated).isTrue().withFailMessage("Executor service did not terminate.");


        log.info("Total successful service calls (some could be retries): {}", totalSuccessfulServiceCalls.get());
        log.info("OptimisticLockExceptions caught by test (should be 0 if @Retryable is effective and maxAttempts not exceeded): {}", optimisticLockExceptionsCaughtInTest.get());

        DynamicPricingRuleEntity finalRule = ruleRepository.findById(baseRule.getId()).orElseThrow();
        long finalVersion = finalRule.getVersion();

        // Each successful *logical* update increments the version.
        // If @Retryable works, all numberOfThreads * updatesPerThread attempts should eventually succeed.
        // The number of successful service calls might be higher than this due to retries.
        // The version should reflect the number of successful *committed* updates.
        assertEquals(numberOfThreads * updatesPerThread, totalSuccessfulServiceCalls.get(),
                "Not all update operations succeeded as expected.");

        // The version should increment for each successful, committed update.
        // initialVersion (0) + total successful updates
        assertEquals(initialVersion + (long)numberOfThreads * updatesPerThread, finalVersion,
                "Version number did not increment as expected for all successful updates.");

        // The 'enabled' flag was toggled twice per thread.
        // If numberOfThreads * updatesPerThread is even, it should return to initial state.
        // If odd, it should be in the opposite state.
        // Initial state: baseRule.isEnabled()
        boolean expectedEnabledState = baseRule.isEnabled();
        if ((numberOfThreads * updatesPerThread) % 2 != 0) {
            expectedEnabledState = !expectedEnabledState;
        }
        assertThat(finalRule.isEnabled()).isEqualTo(expectedEnabledState);

        // Check that parameters from various threads are present (last one wins for a given key, but new keys are added)
        for (int i = 0; i < numberOfThreads; i++) {
            assertThat(finalRule.getParameters()).containsKey("thread" + i + "_update");
        }
    }
}
