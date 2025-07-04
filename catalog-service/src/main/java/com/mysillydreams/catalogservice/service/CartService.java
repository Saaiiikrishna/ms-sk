package com.mysillydreams.catalogservice.service;

import com.mysillydreams.catalogservice.domain.model.CartEntity;
import com.mysillydreams.catalogservice.domain.model.CartItemEntity;
import com.mysillydreams.catalogservice.domain.model.CartStatus;
import com.mysillydreams.catalogservice.domain.model.CatalogItemEntity;
import com.mysillydreams.catalogservice.domain.repository.CartItemRepository;
import com.mysillydreams.catalogservice.domain.repository.CartRepository;
import com.mysillydreams.catalogservice.domain.repository.CatalogItemRepository;
import com.mysillydreams.catalogservice.dto.*;
import com.mysillydreams.catalogservice.exception.InvalidRequestException;
import com.mysillydreams.catalogservice.exception.ResourceNotFoundException;
import com.mysillydreams.catalogservice.config.RedisConfig;
import com.mysillydreams.catalogservice.kafka.event.CartCheckedOutEvent;
import com.mysillydreams.catalogservice.kafka.producer.KafkaProducerService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final StockService stockService; // For reserving/releasing stock
    private final PricingService pricingService; // For getting price details
    private final KafkaProducerService kafkaProducerService;
    private final RedisTemplate<String, CartDto> cartDtoRedisTemplate;
    private final MeterRegistry meterRegistry; // Added MeterRegistry

    // Cache metrics
    private final Counter cartCacheHitCounter;
    private final Counter cartCacheMissCounter;


    @Value("${app.kafka.topic.cart-checked-out}")
    private String cartCheckedOutTopic;

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                       CatalogItemRepository catalogItemRepository, StockService stockService,
                       PricingService pricingService, KafkaProducerService kafkaProducerService,
                       RedisTemplate<String, CartDto> cartDtoRedisTemplate, MeterRegistry meterRegistry) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.stockService = stockService;
        this.pricingService = pricingService;
        this.kafkaProducerService = kafkaProducerService;
        this.cartDtoRedisTemplate = cartDtoRedisTemplate;
        this.meterRegistry = meterRegistry;

        // Initialize counters
        this.cartCacheHitCounter = Counter.builder("catalog.cart.cache.requests")
                                          .tag("result", "hit")
                                          .description("Number of cart cache hits")
                                          .register(meterRegistry);
        this.cartCacheMissCounter = Counter.builder("catalog.cart.cache.requests")
                                           .tag("result", "miss")
                                           .description("Number of cart cache misses")
                                           .register(meterRegistry);
    }

    private String getCartCacheKey(String userId) {
        return RedisConfig.CART_KEY_PREFIX + userId;
    }

    @Transactional
    public CartDto getOrCreateCart(String userId) {
        log.debug("Getting or creating cart for user ID: {}", userId);
        String cacheKey = getCartCacheKey(userId);
        CartDto cachedCart = cartDtoRedisTemplate.opsForValue().get(cacheKey);
        if (cachedCart != null) {
            log.info("Active cart found in cache for user ID: {}", userId);
            cartCacheHitCounter.increment();
            return cachedCart;
        }

        log.debug("No active cart in cache for user ID: {}. Checking database.", userId);
        cartCacheMissCounter.increment();
        CartEntity cartEntity = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseGet(() -> {
                    log.info("No active cart found in DB for user ID: {}. Creating a new one.", userId);
                    CartEntity newCart = CartEntity.builder()
                            .userId(userId)
                            .status(CartStatus.ACTIVE)
                            .build();
                    return cartRepository.save(newCart);
                });

        CartDto cartDto = convertToCartDto(cartEntity);
        cartDtoRedisTemplate.opsForValue().set(cacheKey, cartDto, Duration.ofHours(1)); // Cache for 1 hour
        log.info("Cart for user ID {} cached.", userId);
        return cartDto;
    }

    @Transactional
    public CartDto addItemToCart(String userId, AddItemToCartRequest request) {
        log.info("Adding item {} (qty: {}) to cart for user ID: {}", request.getCatalogItemId(), request.getQuantity(), userId);
        // Ensure cart exists and is loaded (this will also consult cache via getOrCreateCart logic if we call it)
        // To avoid recursive calls or complex cache interaction within a single user flow,
        // it's often better to fetch the DB entity directly here, perform logic, then update cache.
        CartEntity cart = getActiveCartEntityForUser(userId); // Fetches from DB, ensuring we work with persisted state

        CatalogItemEntity item = catalogItemRepository.findById(request.getCatalogItemId())
                .orElseThrow(() -> new ResourceNotFoundException("CatalogItem", "id", request.getCatalogItemId()));

        if (!item.isActive()) {
            throw new InvalidRequestException("Item " + item.getSku() + " is not active and cannot be added to cart.");
        }
        if (item.getItemType() == com.mysillydreams.catalogservice.domain.model.ItemType.SERVICE && request.getQuantity() > 1) {
            // This is a business rule example: typically services are quantity 1, but can vary.
            // log.warn("Adding service {} with quantity {}. Ensure this is intended.", item.getSku(), request.getQuantity());
        }

        // Reserve stock before adding to cart logic
        if (item.getItemType() == com.mysillydreams.catalogservice.domain.model.ItemType.PRODUCT) {
            stockService.reserveStock(item.getId(), request.getQuantity());
            log.info("Stock reserved for item {} (qty: {})", item.getSku(), request.getQuantity());
        }

        Optional<CartItemEntity> existingCartItemOpt = cart.getItems().stream()
                .filter(ci -> ci.getCatalogItem().getId().equals(request.getCatalogItemId()))
                .findFirst();

        CartItemEntity cartItem;
        if (existingCartItemOpt.isPresent()) {
            log.debug("Item {} already in cart. Updating quantity.", item.getSku());
            cartItem = existingCartItemOpt.get();
            // If stock was already reserved for cartItem.getQuantity(), adjust reservation
            // For simplicity, current stockService.reserveStock is absolute, not delta-based for this call.
            // A more robust flow: release old quantity, reserve new total quantity.
            // Or, stockService.reserveStock could take old and new quantity.
            // Current: reserveStock for the *additional* quantity if item exists.
            // This means if item qty was 2, now 5, reserve 3 more.
            // But AddItemToCartRequest has absolute quantity.
            // So, if item exists, we should treat this as an update.
            // Let's assume for now `addItemToCart` with existing item means "set quantity to this new value".
            // Thus, we need to adjust stock reservation based on the *delta*.

            int oldQuantity = cartItem.getQuantity();
            int quantityDelta = request.getQuantity() - oldQuantity;

            if (item.getItemType() == com.mysillydreams.catalogservice.domain.model.ItemType.PRODUCT) {
                if (quantityDelta > 0) {
                    stockService.reserveStock(item.getId(), quantityDelta); // Reserve additional
                } else if (quantityDelta < 0) {
                    stockService.releaseStock(item.getId(), -quantityDelta); // Release surplus
                }
            }
            cartItem.setQuantity(request.getQuantity());
        } else {
            log.debug("Item {} not in cart. Adding new cart item.", item.getSku());
            // PriceDetailDto priceDetail = pricingService.getPriceDetail(item.getId(), request.getQuantity()); // Price per unit
            // Unit price in CartItemEntity should be the price *at the time of adding*
            // For simplicity, let's use current base price or let calculateTotals handle final pricing.
            // The PRD for CartItemEntity has unitPrice and discountApplied.
            // Let's store the base price as unitPrice for now, and discount will be calculated dynamically or at checkout.
            // A better approach: store the discounted unit price from PricingService if rules apply *now*.

            // Get current price detail to store the unit price at the time of adding/updating.
            PriceDetailDto priceDetail = pricingService.getPriceDetail(item.getId(), request.getQuantity());


            cartItem = CartItemEntity.builder()
                    .catalogItem(item)
                    .quantity(request.getQuantity())
                    .unitPrice(priceDetail.getDiscountedUnitPrice()) // Store the actual unit price after potential current discounts
                    // .discountApplied() // This might be complex to store per line if dynamic. Or store ruleId.
                    .build();
            cart.addItem(cartItem); // This also sets cartItem.setCart(cart)
        }

        // cartItem.setUpdatedAt(Instant.now()); // Not strictly needed if CartEntity timestamp updates
        cart.setUpdatedAt(Instant.now()); // Touch cart to update its timestamp

        cart.setUpdatedAt(Instant.now()); // Touch cart to update its timestamp
        CartEntity savedCart = cartRepository.save(cart); // Save cart, cascades to cart items

        CartDto updatedCartDto = convertToCartDto(savedCart);
        cartDtoRedisTemplate.opsForValue().set(getCartCacheKey(userId), updatedCartDto, Duration.ofHours(1));
        log.info("Cart cache updated for user ID {} after adding item.", userId);
        return updatedCartDto;
    }

    @Transactional
    public CartDto updateCartItemQuantity(String userId, UUID catalogItemId, int newQuantity) {
        log.info("Updating quantity for item {} to {} for user ID: {}", catalogItemId, newQuantity, userId);
        if (newQuantity <= 0) {
            throw new InvalidRequestException("New quantity must be positive. To remove, use delete endpoint.");
        }
        CartEntity cart = getActiveCartEntityForUser(userId); // Fetches from DB
        CartItemEntity cartItem = cart.getItems().stream()
                .filter(ci -> ci.getCatalogItem().getId().equals(catalogItemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "catalogItemId", catalogItemId + " in user's cart"));

        CatalogItemEntity item = cartItem.getCatalogItem();

        if (item.getItemType() == com.mysillydreams.catalogservice.domain.model.ItemType.PRODUCT) {
            int oldQuantity = cartItem.getQuantity();
            int quantityDelta = newQuantity - oldQuantity;
            if (quantityDelta > 0) {
                stockService.reserveStock(item.getId(), quantityDelta);
            } else if (quantityDelta < 0) {
                stockService.releaseStock(item.getId(), -quantityDelta);
            }
        }

        cartItem.setQuantity(newQuantity);
        // Update unit price if pricing rules change based on new quantity
        PriceDetailDto priceDetail = pricingService.getPriceDetail(item.getId(), newQuantity);
        cartItem.setUnitPrice(priceDetail.getDiscountedUnitPrice());

        cart.setUpdatedAt(Instant.now());

        cart.setUpdatedAt(Instant.now());
        CartEntity savedCart = cartRepository.save(cart);

        CartDto updatedCartDto = convertToCartDto(savedCart);
        cartDtoRedisTemplate.opsForValue().set(getCartCacheKey(userId), updatedCartDto, Duration.ofHours(1));
        log.info("Cart cache updated for user ID {} after updating item quantity.", userId);
        return updatedCartDto;
    }

    @Transactional
    public CartDto removeCartItem(String userId, UUID catalogItemId) {
        log.info("Removing item {} from cart for user ID: {}", catalogItemId, userId);
        CartEntity cart = getActiveCartEntityForUser(userId); // Fetches from DB
        CartItemEntity cartItem = cart.getItems().stream()
                .filter(ci -> ci.getCatalogItem().getId().equals(catalogItemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "catalogItemId", catalogItemId + " in user's cart"));

        if (cartItem.getCatalogItem().getItemType() == com.mysillydreams.catalogservice.domain.model.ItemType.PRODUCT) {
            stockService.releaseStock(cartItem.getCatalogItem().getId(), cartItem.getQuantity());
            log.info("Released {} units of stock for item {}", cartItem.getQuantity(), cartItem.getCatalogItem().getSku());
        }

        cart.removeItem(cartItem);

        cart.setUpdatedAt(Instant.now());
        CartEntity savedCart = cartRepository.save(cart);

        CartDto updatedCartDto = convertToCartDto(savedCart);
        cartDtoRedisTemplate.opsForValue().set(getCartCacheKey(userId), updatedCartDto, Duration.ofHours(1));
        log.info("Cart cache updated for user ID {} after removing item.", userId);
        return updatedCartDto;
    }

    @Transactional(readOnly = true) // Should not modify state, but calls convertToCartDto which might if not careful
    public CartDto getCartTotals(String userId) {
        log.debug("Calculating totals for user ID: {}", userId);
        // Try cache first for the full DTO as it contains totals
        String cacheKey = getCartCacheKey(userId);
        CartDto cachedCartDto = cartDtoRedisTemplate.opsForValue().get(cacheKey);
        if (cachedCartDto != null) {
            log.info("Cart totals retrieved from cached CartDto for user ID: {}", userId);
            cartCacheHitCounter.increment(); // Also a cache hit for totals if DTO is there
            return cachedCartDto;
        }

        log.debug("CartDto not in cache for totals calculation for user ID: {}. Fetching from DB.", userId);
        cartCacheMissCounter.increment(); // Cache miss for totals
        CartEntity cart = getActiveCartEntityForUser(userId); // Fetches from DB
        CartDto cartDto = convertToCartDto(cart); // Calculates totals
        cartDtoRedisTemplate.opsForValue().set(cacheKey, cartDto, Duration.ofHours(1)); // Cache it
        return cartDto;
    }

    @Transactional
    public CartDto checkoutCart(String userId) {
        log.info("Checking out cart for user ID: {}", userId);
        CartEntity cart = getActiveCartEntityForUser(userId); // Fetches from DB

        if (cart.getItems().isEmpty()) {
            throw new InvalidRequestException("Cannot checkout an empty cart.");
        }

        CartDto cartDtoForEvent = convertToCartDto(cart); // Calculate final state for event

        cart.setStatus(CartStatus.CHECKED_OUT);
        cart.setUpdatedAt(Instant.now());
        CartEntity checkedOutCart = cartRepository.save(cart);

        publishCartCheckedOutEvent(checkedOutCart, cartDtoForEvent);
        log.info("Cart ID: {} checked out successfully for user ID: {}", cart.getId(), userId);

        // Evict from cache as it's no longer an "active" cart in the same sense
        cartDtoRedisTemplate.delete(getCartCacheKey(userId));
        log.info("Checked-out cart removed from active cart cache for user ID: {}", userId);

        return convertToCartDto(checkedOutCart); // Return DTO of the now CHECKED_OUT cart
    }

    // This method now primarily serves internal logic that needs the DB entity.
    // Public facing getOrCreateCart handles caching.
    private CartEntity getActiveCartEntityForUser(String userId) {
        return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseGet(() -> { // Should ideally not be reached if getOrCreateCart is called first by user flows
                    log.warn("No active cart entity found directly for user ID: {}. Creating one. This might indicate an unexpected flow.", userId);
                    CartEntity newCart = CartEntity.builder()
                            .userId(userId)
                            .status(CartStatus.ACTIVE)
                            .build();
                    return cartRepository.save(newCart);
                });
    }


    // This method is crucial and is called by all public methods returning CartDto
    private CartDto convertToCartDto(CartEntity cart) {
        if (cart == null) return null;

        List<CartItemDetailDto> itemDetailDtos = cart.getItems().stream()
                .map(this::convertCartItemToDetailDto) // This calls pricingService.getPriceDetail for each item
                .collect(Collectors.toList());

        BigDecimal subtotal = itemDetailDtos.stream()
                .map(CartItemDetailDto::getLineItemTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiscount = itemDetailDtos.stream()
                // Calculate discount per item: (Original Unit Price - Final Unit Price) * Quantity
                .map(cid -> cid.getOriginalUnitPrice().subtract(cid.getFinalUnitPrice()).multiply(new BigDecimal(cid.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);


        return CartDto.builder()
                .id(cart.getId())
                .userId(cart.getUserId())
                .status(cart.getStatus())
                .items(itemDetailDtos)
                .subtotal(subtotal.setScale(2, RoundingMode.HALF_UP))
                .totalDiscountAmount(totalDiscount.setScale(2, RoundingMode.HALF_UP))
                .finalTotal(subtotal.setScale(2, RoundingMode.HALF_UP)) // Assuming subtotal is after all discounts.
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .version(cart.getVersion())
                .build();
    }

    private CartItemDetailDto convertCartItemToDetailDto(CartItemEntity cartItem) {
        CatalogItemEntity catalogItem = cartItem.getCatalogItem();

        // Use the unitPrice stored in CartItemEntity if we trust it to be the "locked-in" price at time of add/update.
        // However, the current DTO conversion logic in CartService always recalculates price details for display.
        // This ensures the displayed cart is always fresh but means CartItemEntity.unitPrice is less critical for display.
        // Let's stick to recalculating for display via pricingService.getPriceDetail.
        PriceDetailDto currentPriceDetail = pricingService.getPriceDetail(catalogItem.getId(), cartItem.getQuantity());

        BigDecimal originalUnitPrice = catalogItem.getBasePrice();
        BigDecimal finalUnitPrice = currentPriceDetail.getDiscountedUnitPrice(); // This is the one we should use for line total
        BigDecimal discountAppliedPerUnit = originalUnitPrice.subtract(finalUnitPrice);
        BigDecimal lineItemTotal = finalUnitPrice.multiply(new BigDecimal(cartItem.getQuantity()));

        return CartItemDetailDto.builder()
                .cartItemId(cartItem.getId())
                .catalogItemId(catalogItem.getId())
                .sku(catalogItem.getSku())
                .name(catalogItem.getName())
                .quantity(cartItem.getQuantity())
                .originalUnitPrice(originalUnitPrice.setScale(2, RoundingMode.HALF_UP))
                .discountAppliedPerUnit(discountAppliedPerUnit.setScale(2, RoundingMode.HALF_UP))
                .finalUnitPrice(finalUnitPrice.setScale(2, RoundingMode.HALF_UP))
                .lineItemTotal(lineItemTotal.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    private void publishCartCheckedOutEvent(CartEntity cart, CartDto cartDto) {
        List<CartCheckedOutEvent.CartCheckedOutItem> eventItems = cartDto.getItems().stream()
            .map(detailDto -> CartCheckedOutEvent.CartCheckedOutItem.builder() // map from CartItemDetailDto
                .catalogItemId(detailDto.getCatalogItemId())
                .sku(detailDto.getSku())
                .name(detailDto.getName())
                .quantity(detailDto.getQuantity())
                .originalUnitPrice(detailDto.getOriginalUnitPrice())
                .discountAppliedPerUnit(detailDto.getDiscountAppliedPerUnit())
                .finalUnitPrice(detailDto.getFinalUnitPrice())
                .lineItemTotal(detailDto.getLineItemTotal())
                .build())
            .collect(Collectors.toList());

        CartCheckedOutEvent event = CartCheckedOutEvent.builder()
                .eventId(UUID.randomUUID())
                .cartId(cart.getId())
                .userId(cart.getUserId())
                .items(eventItems)
                .subtotal(cartDto.getSubtotal())
                .totalDiscountAmount(cartDto.getTotalDiscountAmount())
                .finalTotal(cartDto.getFinalTotal()) // This is sum of final line item totals
                .checkoutTimestamp(Instant.now())
                .build();

        kafkaProducerService.sendMessage(cartCheckedOutTopic, cart.getId().toString(), event);
    }
}
