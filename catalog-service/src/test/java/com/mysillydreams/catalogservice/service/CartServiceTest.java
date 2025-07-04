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

        // Default PriceDetailDto mock
        when(pricingService.getPriceDetail(any(UUID.class), anyInt())).thenAnswer(invocation -> {
            UUID iId = invocation.getArgument(0);
            int qty = invocation.getArgument(1);
            BigDecimal basePrice = iId.equals(itemId1) ? productItem1.getBasePrice() : serviceItem2.getBasePrice();
            return PriceDetailDto.builder()
                    .itemId(iId).quantity(qty).basePrice(basePrice)
                    .applicableDiscountPercentage(BigDecimal.ZERO)
                    .discountedUnitPrice(basePrice) // No discount by default in tests
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
        // Item 1: Laptop, 1000.00, qty 1. Rule: 10% off for >=1. PriceDetail: discountedUnitPrice=900
        // Item 2: Support, 50.00, qty 2. No discount. PriceDetail: discountedUnitPrice=50

        CartItemEntity cartItem1 = CartItemEntity.builder().id(UUID.randomUUID()).cart(activeCart).catalogItem(productItem1).quantity(1).unitPrice(new BigDecimal("900.00")).build(); // Stored unit price
        CartItemEntity cartItem2 = CartItemEntity.builder().id(UUID.randomUUID()).cart(activeCart).catalogItem(serviceItem2).quantity(2).unitPrice(new BigDecimal("50.00")).build();
        activeCart.getItems().addAll(List.of(cartItem1, cartItem2));

        // Mock PricingService to return specific details for each item
        PriceDetailDto priceDetailItem1 = PriceDetailDto.builder()
            .itemId(itemId1).quantity(1).basePrice(new BigDecimal("1000.00"))
            .applicableDiscountPercentage(new BigDecimal("10.00"))
            .discountedUnitPrice(new BigDecimal("900.00"))
            .totalPrice(new BigDecimal("900.00")).build();
        when(pricingService.getPriceDetail(itemId1, 1)).thenReturn(priceDetailItem1);

        PriceDetailDto priceDetailItem2 = PriceDetailDto.builder()
            .itemId(itemId2).quantity(2).basePrice(new BigDecimal("50.00"))
            .applicableDiscountPercentage(BigDecimal.ZERO)
            .discountedUnitPrice(new BigDecimal("50.00"))
            .totalPrice(new BigDecimal("100.00")).build();
        when(pricingService.getPriceDetail(itemId2, 2)).thenReturn(priceDetailItem2);


        CartDto cartDto = cartService.getOrCreateCart(userId); // This will trigger convertToCartDto via getActiveCartEntityForUser -> convert
        // Or more directly if activeCart is already correctly populated:
        // CartDto cartDto = cartService.convertToCartDto(activeCart); // if convertToCartDto was public
        // For this test, let's assume getOrCreateCart returns a cart that then gets converted.
        // We need to ensure the cartRepository returns our activeCart with items.
        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.of(activeCart));


        CartDto resultDto = cartService.getCartTotals(userId); // This calls getActiveCartEntityForUser then convertToCartDto

        // Item 1: 1 * 900 = 900. Original 1000. Discount 100.
        // Item 2: 2 * 50 = 100. Original 100. Discount 0.
        // Subtotal (sum of final line totals) = 900 + 100 = 1000
        // TotalDiscount = 100 (from item1) + 0 (from item2) = 100
        // FinalTotal = 1000 (same as subtotal here)

        assertThat(resultDto.getSubtotal()).isEqualByComparingTo("1000.00");
        assertThat(resultDto.getTotalDiscountAmount()).isEqualByComparingTo("100.00");
        assertThat(resultDto.getFinalTotal()).isEqualByComparingTo("1000.00");

        assertThat(resultDto.getItems()).hasSize(2);
        CartItemDetailDto detail1 = resultDto.getItems().stream().filter(i -> i.getCatalogItemId().equals(itemId1)).findFirst().get();
        assertThat(detail1.getOriginalUnitPrice()).isEqualByComparingTo("1000.00");
        assertThat(detail1.getFinalUnitPrice()).isEqualByComparingTo("900.00");
        assertThat(detail1.getDiscountAppliedPerUnit()).isEqualByComparingTo("100.00");
        assertThat(detail1.getLineItemTotal()).isEqualByComparingTo("900.00");

        CartItemDetailDto detail2 = resultDto.getItems().stream().filter(i -> i.getCatalogItemId().equals(itemId2)).findFirst().get();
        assertThat(detail2.getOriginalUnitPrice()).isEqualByComparingTo("50.00");
        assertThat(detail2.getFinalUnitPrice()).isEqualByComparingTo("50.00");
        assertThat(detail2.getDiscountAppliedPerUnit()).isEqualByComparingTo("0.00");
        assertThat(detail2.getLineItemTotal()).isEqualByComparingTo("100.00");
    }
}
