package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.*;
import com.mysillydreams.catalogservice.domain.repository.CartItemRepository;
import com.mysillydreams.catalogservice.domain.repository.CartRepository;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.dto.*;
import com.mysillydreams.catalogservice.exception.InvalidRequestException;
import com.mysillydreams.catalogservice.exception.ResourceNotFoundException;
import com.mysillydreams.catalogservice.kafka.event.CartCheckedOutEvent;
import com.mysillydreams.catalogservice.kafka.producer.KafkaProducerService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CartServiceTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository; // Not directly used by CartService methods but good to have if needed
    @Mock private CatalogItemRepository catalogItemRepository;
    @Mock private StockService stockService;
    @Mock private PricingService pricingService;
    @Mock private KafkaProducerService kafkaProducerService;

    @InjectMocks
    private CartService cartService;

    private String userId = "test-user-123";
    private UUID cartId = UUID.randomUUID();
    private UUID itemId1 = UUID.randomUUID();
    private UUID itemId2 = UUID.randomUUID();
    private CatalogItemEntity productItem1;
    private CatalogItemEntity serviceItem2;
    private CartEntity activeCart;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cartService, "cartCheckedOutTopic", "cart.checkedout");

        productItem1 = CatalogItemEntity.builder().id(itemId1).sku("PROD001").name("Laptop").type(ItemType.PRODUCT).active(true).basePrice(new BigDecimal("1000.00")).build();
        serviceItem2 = CatalogItemEntity.builder().id(itemId2).sku("SERV001").name("Support Plan").type(ItemType.SERVICE).active(true).basePrice(new BigDecimal("50.00")).build();

        activeCart = CartEntity.builder().id(cartId).userId(userId).status(CartStatus.ACTIVE).items(new ArrayList<>()).version(0L).createdAt(Instant.now()).updatedAt(Instant.now()).build();

        // Default PriceDetailDto mock - now returns the new structure
        when(pricingService.getPriceDetail(any(UUID.class), anyInt())).thenAnswer(invocation -> {
            UUID iId = invocation.getArgument(0);
            int qty = invocation.getArgument(1);
            CatalogItemEntity currentItem = iId.equals(itemId1) ? productItem1 : serviceItem2;
            BigDecimal basePrice = currentItem.getBasePrice();

            // Simulate a simple PriceDetailDto with no complex components for default mock
            List<PricingComponent> components = List.of(
                PricingComponent.builder().code("CATALOG_BASE_PRICE").description("Base Price").amount(basePrice).build()
            );

            return PriceDetailDto.builder()
                    .itemId(iId)
                    .quantity(qty)
                    .basePrice(basePrice)
                    .overridePrice(null) // No override by default
                    .components(components)
                    .finalUnitPrice(basePrice) // No discount by default
                    .totalPrice(basePrice.multiply(BigDecimal.valueOf(qty)))
                    .build();
        });
    }

    @Test
    void getOrCreateCart_existingActiveCart_returnsIt() {
        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));

        CartDto result = cartService.getOrCreateCart(userId);

        assertThat(result.getId()).isEqualTo(cartId);
        verify(cartRepository, never()).save(any(CartEntity.class));
    }

    @Test
    void getOrCreateCart_noActiveCart_createsAndReturnsNew() {
        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.empty());
        when(cartRepository.save(any(CartEntity.class))).thenReturn(activeCart); // Simulate save returning the cart with an ID

        CartDto result = cartService.getOrCreateCart(userId);

        assertThat(result.getId()).isEqualTo(cartId); // ID from the saved mock entity
        verify(cartRepository).save(argThat(cart -> cart.getUserId().equals(userId) && cart.getStatus() == CartStatus.ACTIVE));
    }

    @Test
    void addItemToCart_newItem_product_reservesStockAndAdds() {
        AddItemToCartRequest request = AddItemToCartRequest.builder().catalogItemId(itemId1).quantity(2).build();
        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));
        when(catalogItemRepository.findById(itemId1)).thenReturn(Optional.of(productItem1));
        when(stockService.reserveStock(itemId1, 2)).thenReturn(mock(StockLevelDto.class)); // Simulate stock reservation
        when(cartRepository.save(any(CartEntity.class))).thenReturn(activeCart);


        CartDto result = cartService.addItemToCart(userId, request);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getCatalogItemId()).isEqualTo(itemId1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);
        // Verify that the CartItemEntity stored the finalUnitPrice from PriceDetailDto
        assertThat(activeCart.getItems().get(0).getUnitPrice()).isEqualByComparingTo(productItem1.getBasePrice());
        verify(stockService).reserveStock(itemId1, 2);
        verify(cartRepository).save(activeCart);
    }

    @Test
    void addItemToCart_itemInactive_throwsException() {
        productItem1.setActive(false); // Make item inactive
        AddItemToCartRequest request = AddItemToCartRequest.builder().catalogItemId(itemId1).quantity(1).build();
        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));
        when(catalogItemRepository.findById(itemId1)).thenReturn(Optional.of(productItem1));

        assertThrows(InvalidRequestException.class, () -> cartService.addItemToCart(userId, request));
        verify(stockService, never()).reserveStock(any(), anyInt());
    }


    @Test
    void addItemToCart_existingItem_updatesQuantityAndAdjustsStock() {
        // Setup: item1 already in cart with quantity 1
        CartItemEntity existingCartItem = CartItemEntity.builder().id(UUID.randomUUID()).cart(activeCart).catalogItem(productItem1).quantity(1).unitPrice(productItem1.getBasePrice()).build();
        activeCart.getItems().add(existingCartItem);

        AddItemToCartRequest request = AddItemToCartRequest.builder().catalogItemId(itemId1).quantity(3).build(); // New total quantity 3

        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));
        when(catalogItemRepository.findById(itemId1)).thenReturn(Optional.of(productItem1)); // Not strictly needed if item already in cart, but good for consistency
        // Expect stockService to be called for the delta quantity (3 - 1 = 2)
        when(stockService.reserveStock(itemId1, 2)).thenReturn(mock(StockLevelDto.class));
        when(cartRepository.save(any(CartEntity.class))).thenReturn(activeCart);

        CartDto result = cartService.addItemToCart(userId, request);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(3);
        verify(stockService).reserveStock(itemId1, 2); // Delta reserved
    }

     @Test
    void addItemToCart_existingItem_decreaseQuantity_releasesStock() {
        CartItemEntity existingCartItem = CartItemEntity.builder().id(UUID.randomUUID()).cart(activeCart).catalogItem(productItem1).quantity(5).unitPrice(productItem1.getBasePrice()).build();
        activeCart.getItems().add(existingCartItem);

        AddItemToCartRequest request = AddItemToCartRequest.builder().catalogItemId(itemId1).quantity(2).build(); // New total quantity 2

        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));
        when(catalogItemRepository.findById(itemId1)).thenReturn(Optional.of(productItem1));
        when(stockService.releaseStock(itemId1, 3)).thenReturn(mock(StockLevelDto.class)); // 5 - 2 = 3 released
        when(cartRepository.save(any(CartEntity.class))).thenReturn(activeCart);

        CartDto result = cartService.addItemToCart(userId, request);

        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);
        verify(stockService).releaseStock(itemId1, 3);
    }


    @Test
    void updateCartItemQuantity_valid_updatesAndAdjustsStock() {
        CartItemEntity cartItem = CartItemEntity.builder().id(UUID.randomUUID()).cart(activeCart).catalogItem(productItem1).quantity(2).unitPrice(productItem1.getBasePrice()).build();
        activeCart.getItems().add(cartItem);

        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));
        // No need to mock catalogItemRepository.findById as item is fetched from cartItem.getCatalogItem()
        when(stockService.reserveStock(itemId1, 3)).thenReturn(mock(StockLevelDto.class)); // 5 (new) - 2 (old) = 3
        when(cartRepository.save(any(CartEntity.class))).thenReturn(activeCart);


        CartDto result = cartService.updateCartItemQuantity(userId, itemId1, 5);

        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(5);
        verify(stockService).reserveStock(itemId1, 3);
    }

    @Test
    void removeCartItem_product_releasesStockAndRemoves() {
        CartItemEntity cartItem = CartItemEntity.builder().id(UUID.randomUUID()).cart(activeCart).catalogItem(productItem1).quantity(2).unitPrice(productItem1.getBasePrice()).build();
        activeCart.getItems().add(cartItem);

        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));
        when(stockService.releaseStock(itemId1, 2)).thenReturn(mock(StockLevelDto.class));
        when(cartRepository.save(any(CartEntity.class))).thenAnswer(invocation -> {
            // Simulate removal for DTO conversion
            CartEntity c = invocation.getArgument(0);
            c.getItems().removeIf(ci -> ci.getCatalogItem().getId().equals(itemId1));
            return c;
        });


        CartDto result = cartService.removeCartItem(userId, itemId1);

        assertThat(result.getItems()).isEmpty();
        verify(stockService).releaseStock(itemId1, 2);
        verify(cartRepository).save(activeCart); // Cart itself is saved
    }

    @Test
    void checkoutCart_validCart_updatesStatusAndPublishesEvent() {
        CartItemEntity cartItem = CartItemEntity.builder().id(UUID.randomUUID()).cart(activeCart).catalogItem(productItem1).quantity(1).unitPrice(productItem1.getBasePrice()).build();
        activeCart.getItems().add(cartItem);

        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));
        when(cartRepository.save(any(CartEntity.class))).thenAnswer(invocation -> {
            CartEntity c = invocation.getArgument(0);
            c.setStatus(CartStatus.CHECKED_OUT); // Simulate status change by save
            return c;
        });

        CartDto result = cartService.checkoutCart(userId);

        assertThat(result.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
        verify(cartRepository).save(argThat(c -> c.getStatus() == CartStatus.CHECKED_OUT));

        ArgumentCaptor<CartCheckedOutEvent> eventCaptor = ArgumentCaptor.forClass(CartCheckedOutEvent.class);
        verify(kafkaProducerService).sendMessage(eq("cart.checkedout"), eq(cartId.toString()), eventCaptor.capture());

        CartCheckedOutEvent event = eventCaptor.getValue();
        assertThat(event.getCartId()).isEqualTo(cartId);
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getItems()).hasSize(1);
        assertThat(event.getItems().get(0).getCatalogItemId()).isEqualTo(itemId1);
    }

    @Test
    void checkoutCart_emptyCart_throwsException() {
        // Active cart is empty by default in setup for this test
        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));

        assertThrows(InvalidRequestException.class, () -> cartService.checkoutCart(userId));
        verify(kafkaProducerService, never()).sendMessage(anyString(), anyString(), any());
    }

    @Test
    void convertToCartDto_calculatesTotalsCorrectly() {
    void convertToCartDto_calculatesTotalsCorrectly_withNewPriceDetailStructure() {
        // Item 1 (productItem1): basePrice 1000.00. Let's say 10% bulk discount applies.
        // Item 2 (serviceItem2): basePrice 50.00. No discounts.

        // Setup cart items in the activeCart
        CartItemEntity cartItem1Entity = CartItemEntity.builder()
            .id(UUID.randomUUID()).cart(activeCart).catalogItem(productItem1).quantity(1)
            .unitPrice(new BigDecimal("900.00")) // This is the final unit price stored from a previous add/update
            .build();
        CartItemEntity cartItem2Entity = CartItemEntity.builder()
            .id(UUID.randomUUID()).cart(activeCart).catalogItem(serviceItem2).quantity(2)
            .unitPrice(new BigDecimal("50.00")) // Stored final unit price
            .build();
        activeCart.getItems().addAll(List.of(cartItem1Entity, cartItem2Entity));

        // Mock PricingService.getPriceDetail to return new DTO structure
        // For productItem1 (qty 1) - simulate 10% discount
        List<PricingComponent> componentsItem1 = List.of(
            PricingComponent.builder().code("CATALOG_BASE_PRICE").description("Base").amount(new BigDecimal("1000.00")).build(),
            PricingComponent.builder().code("BULK_DISCOUNT").description("10% off").amount(new BigDecimal("-100.00")).build()
        );
        PriceDetailDto priceDetailItem1 = PriceDetailDto.builder()
            .itemId(itemId1).quantity(1).basePrice(new BigDecimal("1000.00")).overridePrice(null)
            .components(componentsItem1)
            .finalUnitPrice(new BigDecimal("900.00"))
            .totalPrice(new BigDecimal("900.00"))
            .build();
        when(pricingService.getPriceDetail(itemId1, 1)).thenReturn(priceDetailItem1);

        // For serviceItem2 (qty 2) - no discount
         List<PricingComponent> componentsItem2 = List.of(
            PricingComponent.builder().code("CATALOG_BASE_PRICE").description("Base").amount(new BigDecimal("50.00")).build()
        );
        PriceDetailDto priceDetailItem2 = PriceDetailDto.builder()
            .itemId(itemId2).quantity(2).basePrice(new BigDecimal("50.00")).overridePrice(null)
            .components(componentsItem2)
            .finalUnitPrice(new BigDecimal("50.00"))
            .totalPrice(new BigDecimal("100.00")) // 50 * 2
            .build();
        when(pricingService.getPriceDetail(itemId2, 2)).thenReturn(priceDetailItem2);

        // When CartService tries to get the cart (e.g. for getCartTotals)
        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));

        CartDto resultDto = cartService.getCartTotals(userId); // This calls convertToCartDto internally

        // Expected calculations based on PriceDetailDto values:
        // Item 1: Final Unit Price = 900.00, Qty = 1, Line Total = 900.00. Original Base = 1000.00. Discount = 100.00
        // Item 2: Final Unit Price = 50.00, Qty = 2, Line Total = 100.00. Original Base = 50.00. Discount = 0.00

        // Cart Totals:
        // Subtotal (sum of line totals using finalUnitPrice) = 900.00 + 100.00 = 1000.00
        // Total Discount Amount = 100.00 (from item1) + 0.00 (from item2) = 100.00
        // Final Total = Subtotal = 1000.00 (assuming no further cart-level adjustments)

        assertThat(resultDto.getSubtotal()).isEqualByComparingTo("1000.00");
        assertThat(resultDto.getTotalDiscountAmount()).isEqualByComparingTo("100.00");
        assertThat(resultDto.getFinalTotal()).isEqualByComparingTo("1000.00");

        assertThat(resultDto.getItems()).hasSize(2);
        CartItemDetailDto detail1 = resultDto.getItems().stream().filter(i -> i.getCatalogItemId().equals(itemId1)).findFirst().orElseThrow();
        assertThat(detail1.getOriginalUnitPrice()).isEqualByComparingTo("1000.00"); // from PriceDetailDto.basePrice
        assertThat(detail1.getFinalUnitPrice()).isEqualByComparingTo("900.00");   // from PriceDetailDto.finalUnitPrice
        assertThat(detail1.getDiscountAppliedPerUnit()).isEqualByComparingTo("100.00"); // base - final
        assertThat(detail1.getLineItemTotal()).isEqualByComparingTo("900.00");    // from PriceDetailDto.totalPrice (for that line)

        CartItemDetailDto detail2 = resultDto.getItems().stream().filter(i -> i.getCatalogItemId().equals(itemId2)).findFirst().orElseThrow();
        assertThat(detail2.getOriginalUnitPrice()).isEqualByComparingTo("50.00");
        assertThat(detail2.getFinalUnitPrice()).isEqualByComparingTo("50.00");
        assertThat(detail2.getDiscountAppliedPerUnit()).isEqualByComparingTo("0.00");
        assertThat(detail2.getLineItemTotal()).isEqualByComparingTo("100.00");
    }
}
