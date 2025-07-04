package com.mysillydreams.catalogservice.repository;

import com.mysillydreams.catalogservice.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CartRepositoryIntegrationTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CatalogItemRepository catalogItemRepository;

    private CatalogItemEntity item1;
    private CatalogItemEntity item2;
    private String testUserId = "user-" + UUID.randomUUID();

    @BeforeEach
    @Sql("/sql/delete-all-data.sql")
    void setUp() {
        CategoryEntity category = categoryRepository.save(CategoryEntity.builder()
                .name("Test Category")
                .type(ItemType.PRODUCT)
                .path("/testcat/")
                .build());

        item1 = catalogItemRepository.save(CatalogItemEntity.builder()
                .category(category)
                .sku("ITEM001")
                .name("Test Item 1")
                .itemType(ItemType.PRODUCT)
                .basePrice(new BigDecimal("10.00"))
                .active(true)
                .build());

        item2 = catalogItemRepository.save(CatalogItemEntity.builder()
                .category(category)
                .sku("ITEM002")
                .name("Test Item 2")
                .itemType(ItemType.PRODUCT)
                .basePrice(new BigDecimal("20.00"))
                .active(true)
                .build());
    }

    @Test
    void whenSaveCartWithItems_thenSuccess() {
        CartEntity cart = CartEntity.builder()
                .userId(testUserId)
                .status(CartStatus.ACTIVE)
                .build();

        CartItemEntity cartItem1 = CartItemEntity.builder()
                .catalogItem(item1)
                .quantity(2)
                .unitPrice(item1.getBasePrice())
                .build();
        // cart.addItem(cartItem1); // This helper sets cartItem.setCart(this)

        CartItemEntity cartItem2 = CartItemEntity.builder()
                .catalogItem(item2)
                .quantity(1)
                .unitPrice(item2.getBasePrice())
                .build();
        // cart.addItem(cartItem2);

        // For new carts with new items, it's often easier to save cart first, then items, or use cascade persist
        // Or, if not using cascade persist on Cart.items, save items individually after setting their cart.
        // Simplest for this test: Save cart, then set cart on items and save items.
        // Or, if helper methods correctly set bidirectional and cascade is appropriate:
        cart.addItem(cartItem1); // sets cartItem1.setCart(cart)
        cart.addItem(cartItem2); // sets cartItem2.setCart(cart)


        CartEntity savedCart = cartRepository.save(cart); // This should cascade save cart items if CartEntity.items has CascadeType.PERSIST or ALL

        assertThat(savedCart.getId()).isNotNull();
        assertThat(savedCart.getUserId()).isEqualTo(testUserId);
        assertThat(savedCart.getItems()).hasSize(2);

        // Verify items were saved and associated
        CartEntity fetchedCart = cartRepository.findById(savedCart.getId()).orElseThrow();
        assertThat(fetchedCart.getItems()).hasSize(2);
        assertThat(fetchedCart.getItems()).extracting(ci -> ci.getCatalogItem().getSku())
                .containsExactlyInAnyOrder("ITEM001", "ITEM002");
    }

    @Test
    void whenFindByUserIdAndStatus_thenSuccess() {
        CartEntity activeCart = CartEntity.builder().userId(testUserId).status(CartStatus.ACTIVE).build();
        cartRepository.save(activeCart);

        CartEntity checkedOutCart = CartEntity.builder().userId(testUserId).status(CartStatus.CHECKED_OUT).build();
        cartRepository.save(checkedOutCart);

        Optional<CartEntity> foundCart = cartRepository.findByUserIdAndStatus(testUserId, CartStatus.ACTIVE);
        assertThat(foundCart).isPresent();
        assertThat(foundCart.get().getId()).isEqualTo(activeCart.getId());
    }

    @Test
    void whenDeleteItemFromCart_thenCartItemIsRemoved() {
        CartEntity cart = CartEntity.builder().userId(testUserId).status(CartStatus.ACTIVE).build();
        CartItemEntity cItem1 = CartItemEntity.builder().catalogItem(item1).quantity(1).unitPrice(item1.getBasePrice()).build();
        cart.addItem(cItem1);
        cartRepository.save(cart);

        CartEntity savedCart = cartRepository.findByUserIdAndStatus(testUserId, CartStatus.ACTIVE).orElseThrow();
        assertThat(savedCart.getItems()).hasSize(1);
        UUID cartItemIdToRemove = savedCart.getItems().get(0).getId();

        // Option 1: Using CartItemRepository
        cartItemRepository.deleteById(cartItemIdToRemove);
        //entityManager.flush(); // ensure change is propagated if checking immediately

        CartEntity updatedCart = cartRepository.findById(savedCart.getId()).orElseThrow();
        assertThat(updatedCart.getItems()).isEmpty();

        // Option 2: Using CartEntity's collection management (if orphanRemoval=true)
        // CartEntity cartToUpdate = cartRepository.findById(savedCart.getId()).orElseThrow();
        // cartToUpdate.getItems().removeIf(item -> item.getId().equals(cartItemIdToRemove));
        // cartRepository.save(cartToUpdate);
        // CartEntity finalCart = cartRepository.findById(savedCart.getId()).orElseThrow();
        // assertThat(finalCart.getItems()).isEmpty();
    }


    @Test
    void findByCartIdAndCatalogItemId_whenExists_returnsCartItem() {
        CartEntity cart = cartRepository.save(CartEntity.builder().userId(testUserId).status(CartStatus.ACTIVE).build());
        CartItemEntity cartItem = cartItemRepository.save(
            CartItemEntity.builder().cart(cart).catalogItem(item1).quantity(1).unitPrice(item1.getBasePrice()).build()
        );

        Optional<CartItemEntity> found = cartItemRepository.findByCartIdAndCatalogItemId(cart.getId(), item1.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(cartItem.getId());
    }

    @Test
    void findByCartIdAndCatalogItemId_whenNotExists_returnsEmpty() {
        CartEntity cart = cartRepository.save(CartEntity.builder().userId(testUserId).status(CartStatus.ACTIVE).build());
        // Item2 not added to this cart
        Optional<CartItemEntity> found = cartItemRepository.findByCartIdAndCatalogItemId(cart.getId(), item2.getId());
        assertThat(found).isNotPresent();
    }
}
