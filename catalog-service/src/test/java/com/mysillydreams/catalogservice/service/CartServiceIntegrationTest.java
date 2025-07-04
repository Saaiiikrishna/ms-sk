package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.*;
import com.mysillydreams.catalogservice.domain.repository.*;
import com.mysillydreams.catalogservice.dto.*;
import com.mysillydreams.catalogservice.kafka.event.CartCheckedOutEvent;
import com.mysillydreams.catalogservice.kafka.event.StockLevelChangedEvent;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.List;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        "${app.kafka.topic.cart-checked-out}",
        "${app.kafka.topic.stock-changed}" // For stock events from CartService's interaction with StockService
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class CartServiceIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgresqlContainer =
            new PostgreSQLContainer<>("postgres:15.3-alpine")
                    .withDatabaseName("test_catalog_db_cart_int")
                    .withUsername("testuser")
                    .withPassword("testpassword");

    static {
        System.setProperty("spring.datasource.url", postgresqlContainer.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgresqlContainer.getUsername());
        System.setProperty("spring.datasource.password", postgresqlContainer.getPassword());
        System.setProperty("spring.datasource.driver-class-name", "org.testcontainers.jdbc.ContainerDatabaseDriver");
    }

    @Autowired private CartService cartService;
    @Autowired private StockService stockService; // Direct interaction for setup, or rely on CartService calling it
    @Autowired private PricingService pricingService; // For setting up rules if needed

    @Autowired private CatalogItemRepository itemRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private StockLevelRepository stockLevelRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private StockTransactionRepository stockTransactionRepository;
    @Autowired private BulkPricingRuleRepository bulkPricingRuleRepository;


    @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Value("${app.kafka.topic.cart-checked-out}") private String cartCheckedOutTopic;
    @Value("${app.kafka.topic.stock-changed}") private String stockChangedTopic;


    private KafkaMessageListenerContainer<String, Object> listenerContainer;
    private BlockingQueue<ConsumerRecord<String, Object>> consumerRecords;

    private String testUserId = "user-cart-int-test";
    private CatalogItemEntity product1;
    private CatalogItemEntity product2;

    @BeforeEach
    void setUp() {
        consumerRecords = new LinkedBlockingQueue<>();
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("cartIntTestGroup", "true", embeddedKafkaBroker);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mysillydreams.catalogservice.kafka.event");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);


        DefaultKafkaConsumerFactory<String, Object> cf = new DefaultKafkaConsumerFactory<>(consumerProps);

        ContainerProperties containerProps = new ContainerProperties(cartCheckedOutTopic, stockChangedTopic);
        listenerContainer = new KafkaMessageListenerContainer<>(cf, containerProps);
        listenerContainer.setupMessageListener((MessageListener<String, Object>) consumerRecords::add);
        listenerContainer.start();
        ContainerTestUtils.waitForAssignment(listenerContainer, embeddedKafkaBroker.getPartitionsPerTopic());

        // Clean up previous test data
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        stockTransactionRepository.deleteAll();
        stockLevelRepository.deleteAll();
        bulkPricingRuleRepository.deleteAll();
        itemRepository.deleteAll();
        categoryRepository.deleteAll();


        CategoryEntity cat = categoryRepository.save(CategoryEntity.builder().name("Integration Cart Test Category").type(ItemType.PRODUCT).path("/intcartcat/").build());
        product1 = itemRepository.save(CatalogItemEntity.builder().category(cat).sku("CART-INT-P1").name("Cart Test Product 1").itemType(ItemType.PRODUCT).basePrice(new BigDecimal("10.00")).active(true).build());
        product2 = itemRepository.save(CatalogItemEntity.builder().category(cat).sku("CART-INT-P2").name("Cart Test Product 2").itemType(ItemType.PRODUCT).basePrice(new BigDecimal("20.00")).active(true).build());

        // Manually create stock for these items as ItemService.createItem would do
        stockLevelRepository.save(StockLevelEntity.builder().catalogItem(product1).quantityOnHand(50).reorderLevel(5).build());
        stockLevelRepository.save(StockLevelEntity.builder().catalogItem(product2).quantityOnHand(30).reorderLevel(3).build());
    }

    @AfterEach
    void tearDown() {
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }

    @Test
    void addItemsToCart_updatesStockAndCartCorrectly_producesStockEvents() throws InterruptedException {
        CartDto cart = cartService.getOrCreateCart(testUserId);
        assertNotNull(cart.getId());

        // Add product1 (qty 2)
        cartService.addItemToCart(testUserId, AddItemToCartRequest.builder().catalogItemId(product1.getId()).quantity(2).build());

        ConsumerRecord<String, Object> stockEvent1 = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(stockEvent1, "Stock event for product1 add not received");
        assertEquals(stockChangedTopic, stockEvent1.topic());
        assertThat(stockEvent1.value()).isInstanceOf(StockLevelChangedEvent.class);
        StockLevelChangedEvent slEvent1 = (StockLevelChangedEvent) stockEvent1.value();
        assertThat(slEvent1.getItemId()).isEqualTo(product1.getId());
        assertThat(slEvent1.getQuantityChanged()).isEqualTo(-2); // Reserved
        assertThat(slEvent1.getQuantityAfter()).isEqualTo(48); // 50 - 2

        StockLevelEntity p1Stock = stockLevelRepository.findByCatalogItemId(product1.getId()).orElseThrow();
        assertThat(p1Stock.getQuantityOnHand()).isEqualTo(48);

        CartEntity updatedCartEntity = cartRepository.findById(cart.getId()).orElseThrow();
        assertThat(updatedCartEntity.getItems()).hasSize(1);
        assertThat(updatedCartEntity.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(updatedCartEntity.getItems().get(0).getCatalogItem().getId()).isEqualTo(product1.getId());
        // Check unit price stored in cart item (should be base price as no rules set up yet)
        assertThat(updatedCartEntity.getItems().get(0).getUnitPrice()).isEqualByComparingTo("10.00");


        // Add product2 (qty 3)
        cartService.addItemToCart(testUserId, AddItemToCartRequest.builder().catalogItemId(product2.getId()).quantity(3).build());
        ConsumerRecord<String, Object> stockEvent2 = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(stockEvent2, "Stock event for product2 add not received");
        StockLevelChangedEvent slEvent2 = (StockLevelChangedEvent) stockEvent2.value();
        assertThat(slEvent2.getItemId()).isEqualTo(product2.getId());
        assertThat(slEvent2.getQuantityChanged()).isEqualTo(-3);
        assertThat(slEvent2.getQuantityAfter()).isEqualTo(27); // 30 - 3

        CartDto finalCartDto = cartService.getCartTotals(testUserId); // Recalculates and gives DTO
        assertThat(finalCartDto.getItems()).hasSize(2);
        // Line item 1 (product1, base 10.00): 2 * 10.00 = 20.00
        // Line item 2 (product2, base 20.00): 3 * 20.00 = 60.00
        // Subtotal (sum of final line totals) = 20.00 + 60.00 = 80.00
        // TotalDiscount = 0 (no rules applied)
        // FinalTotal = 80.00
        assertThat(finalCartDto.getSubtotal()).isEqualByComparingTo("80.00");
        assertThat(finalCartDto.getTotalDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(finalCartDto.getFinalTotal()).isEqualByComparingTo("80.00");

        CartItemDetailDto p1Detail = finalCartDto.getItems().stream().filter(i -> i.getCatalogItemId().equals(product1.getId())).findFirst().orElseThrow();
        assertThat(p1Detail.getFinalUnitPrice()).isEqualByComparingTo("10.00");
        assertThat(p1Detail.getLineItemTotal()).isEqualByComparingTo("20.00");

        CartItemDetailDto p2Detail = finalCartDto.getItems().stream().filter(i -> i.getCatalogItemId().equals(product2.getId())).findFirst().orElseThrow();
        assertThat(p2Detail.getFinalUnitPrice()).isEqualByComparingTo("20.00");
        assertThat(p2Detail.getLineItemTotal()).isEqualByComparingTo("60.00");
    }

    @Test
    void updateCartItemQuantity_increasesAndDecreases_adjustsStockCorrectly() throws InterruptedException {
        CartDto cart = cartService.getOrCreateCart(testUserId);
        cartService.addItemToCart(testUserId, AddItemToCartRequest.builder().catalogItemId(product1.getId()).quantity(2).build()); // Stock 48
        consumerRecords.poll(1, TimeUnit.SECONDS); // consume add item stock event

        // Increase quantity of product1 from 2 to 5 (delta +3, so reserve 3 more)
        cartService.updateCartItemQuantity(testUserId, product1.getId(), 5);
        StockLevelEntity p1StockAfterIncrease = stockLevelRepository.findByCatalogItemId(product1.getId()).orElseThrow();
        assertThat(p1StockAfterIncrease.getQuantityOnHand()).isEqualTo(45); // 48 - 3 = 45
        ConsumerRecord<String, Object> stockEventIncrease = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(stockEventIncrease);
        assertThat(((StockLevelChangedEvent)stockEventIncrease.value()).getQuantityChanged()).isEqualTo(-3); // Reserved 3 more

        // Decrease quantity of product1 from 5 to 1 (delta -4, so release 4)
        cartService.updateCartItemQuantity(testUserId, product1.getId(), 1);
        StockLevelEntity p1StockAfterDecrease = stockLevelRepository.findByCatalogItemId(product1.getId()).orElseThrow();
        assertThat(p1StockAfterDecrease.getQuantityOnHand()).isEqualTo(49); // 45 + 4 = 49
        ConsumerRecord<String, Object> stockEventDecrease = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(stockEventDecrease);
        assertThat(((StockLevelChangedEvent)stockEventDecrease.value()).getQuantityChanged()).isEqualTo(4); // Released 4
    }


    @Test
    void removeCartItem_releasesStock() throws InterruptedException {
        CartDto cart = cartService.getOrCreateCart(testUserId);
        cartService.addItemToCart(testUserId, AddItemToCartRequest.builder().catalogItemId(product1.getId()).quantity(5).build()); // Stock 45
        consumerRecords.poll(1, TimeUnit.SECONDS);

        cartService.removeCartItem(testUserId, product1.getId());
        StockLevelEntity p1StockAfterRemove = stockLevelRepository.findByCatalogItemId(product1.getId()).orElseThrow();
        assertThat(p1StockAfterRemove.getQuantityOnHand()).isEqualTo(50); // 50 (initial) - 5 (reserved) + 5 (released) = 50

        ConsumerRecord<String, Object> stockEventRemove = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(stockEventRemove);
        assertThat(((StockLevelChangedEvent)stockEventRemove.value()).getQuantityChanged()).isEqualTo(5); // Released 5

        CartDto finalCartDto = cartService.getCartTotals(testUserId);
        assertThat(finalCartDto.getItems()).isEmpty();
    }

    @Test
    void checkoutCart_updatesStatusAndPublishesEvent() throws InterruptedException {
        CartDto cart = cartService.getOrCreateCart(testUserId);
        cartService.addItemToCart(testUserId, AddItemToCartRequest.builder().catalogItemId(product1.getId()).quantity(2).build());
        cartService.addItemToCart(testUserId, AddItemToCartRequest.builder().catalogItemId(product2.getId()).quantity(1).build());
        consumerRecords.clear(); // Clear stock events

        CartDto checkedOutCartDto = cartService.checkoutCart(testUserId);
        assertEquals(CartStatus.CHECKED_OUT, checkedOutCartDto.getStatus());

        CartEntity cartEntity = cartRepository.findById(cart.getId()).orElseThrow();
        assertEquals(CartStatus.CHECKED_OUT, cartEntity.getStatus());

        ConsumerRecord<String, Object> checkoutEventRecord = consumerRecords.poll(5, TimeUnit.SECONDS);
        assertNotNull(checkoutEventRecord, "CartCheckedOutEvent not received");
        assertEquals(cartCheckedOutTopic, checkoutEventRecord.topic());
        assertThat(checkoutEventRecord.value()).isInstanceOf(CartCheckedOutEvent.class);
        CartCheckedOutEvent event = (CartCheckedOutEvent) checkoutEventRecord.value();
        assertThat(event.getCartId()).isEqualTo(cart.getId());
        assertThat(event.getUserId()).isEqualTo(testUserId);
        assertThat(event.getItems()).hasSize(2);
        // Item 1 (product1, base 10.00): 2 * 10.00 = 20.00
        // Item 2 (product2, base 20.00): 1 * 20.00 = 20.00
        // FinalTotal (sum of final line totals) = 20.00 + 20.00 = 40.00
        assertThat(event.getFinalTotal()).isEqualByComparingTo("40.00");
        assertThat(event.getSubtotal()).isEqualByComparingTo("40.00");
        assertThat(event.getTotalDiscountAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void cartTotals_withBulkDiscount_calculatesCorrectly() {
        // Product 1: base 10.00. Rule: 10% off for >= 5 items
        pricingService.createBulkPricingRule(CreateBulkPricingRuleRequest.builder()
            .itemId(product1.getId()).minQuantity(5).discountPercentage(new BigDecimal("10.00")).active(true).build());
        consumerRecords.clear(); // Ignore pricing rule event if any

        cartService.getOrCreateCart(testUserId); // Ensure cart exists
        cartService.addItemToCart(testUserId, AddItemToCartRequest.builder().catalogItemId(product1.getId()).quantity(10).build());
        consumerRecords.clear(); // Ignore stock event

        CartDto cartDto = cartService.getCartTotals(testUserId);

        // Product 1: 10 items. Base price 10.00. Rule: 10% off for >= 5 items.
        // PricingService.getPriceDetail will return:
        //   basePrice: 10.00
        //   finalUnitPrice: 9.00
        //   totalPrice (for 10 items): 90.00
        //   components: [CATALOG_BASE_PRICE (10.00), BULK_DISCOUNT (-1.00 per unit effectively, or -10.00 total for line)]
        // CartItemDetailDto should reflect this:
        //   originalUnitPrice: 10.00
        //   finalUnitPrice: 9.00
        //   discountAppliedPerUnit: 1.00
        //   lineItemTotal: 90.00
        // CartDto totals:
        //   subtotal: 90.00 (sum of lineItemTotals)
        //   totalDiscountAmount: 10.00 ( (10.00 - 9.00) * 10 )
        //   finalTotal: 90.00

        assertThat(cartDto.getItems()).hasSize(1);
        CartItemDetailDto itemDetail = cartDto.getItems().get(0);
        assertThat(itemDetail.getCatalogItemId()).isEqualTo(product1.getId());
        assertThat(itemDetail.getQuantity()).isEqualTo(10);
        assertThat(itemDetail.getOriginalUnitPrice()).isEqualByComparingTo("10.00"); // From CatalogItem.basePrice via PriceDetailDto.basePrice
        assertThat(itemDetail.getFinalUnitPrice()).isEqualByComparingTo("9.00");     // From PriceDetailDto.finalUnitPrice
        assertThat(itemDetail.getDiscountAppliedPerUnit()).isEqualByComparingTo("1.00"); // Derived: original - final
        assertThat(itemDetail.getLineItemTotal()).isEqualByComparingTo("90.00");    // From PriceDetailDto.totalPrice (for the line)

        assertThat(cartDto.getSubtotal()).isEqualByComparingTo("90.00");
        assertThat(cartDto.getTotalDiscountAmount()).isEqualByComparingTo("10.00");
        assertThat(cartDto.getFinalTotal()).isEqualByComparingTo("90.00");
    }
}
