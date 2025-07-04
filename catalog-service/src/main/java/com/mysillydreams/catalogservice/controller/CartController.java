package com.mysillydreams.catalogservice.controller;

import com.mysillydreams.catalogservice.dto.AddItemToCartRequest;
import com.mysillydreams.catalogservice.dto.CartDto;
import com.mysillydreams.catalogservice.dto.UpdateCartItemRequest;
import com.mysillydreams.catalogservice.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
// import org.springframework.security.oauth2.jwt.Jwt; // Example if using JWTs directly
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.security.Principal; // Simpler way to get username/subject
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Cart Management", description = "APIs for managing user shopping carts")
@SecurityRequirement(name = "bearerAuth") // Assuming bearer token authentication
public class CartController {

    private final CartService cartService;

    // Helper to get userId from Principal (e.g., JWT subject)
    // In a real app, this might come from a utility or directly from @AuthenticationPrincipal Jwt jwt -> jwt.getSubject()
    private String getUserId(Principal principal) {
        if (principal == null) {
            // This should ideally be caught by Spring Security if endpoint is secured
            throw new IllegalStateException("User principal not found. Endpoint might not be secured correctly or token is missing.");
        }
        return principal.getName(); // Default for username/password or JWT subject
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get or create user's cart", description = "Retrieves the active cart for the authenticated user, or creates one if none exists.")
    @ApiResponse(responseCode = "200", description = "Cart retrieved or created successfully")
    public ResponseEntity<CartDto> getOrCreateCart(Principal principal) {
        CartDto cart = cartService.getOrCreateCart(getUserId(principal));
        return ResponseEntity.ok(cart);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get user's active cart", description = "Retrieves the authenticated user's active cart details.")
    @ApiResponse(responseCode = "200", description = "Cart retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Active cart not found for user") // If getOrCreateCart logic is not used here
    public ResponseEntity<CartDto> getActiveCart(Principal principal) {
        // This effectively does the same as getOrCreateCart if we always want to return/create one.
        // If we strictly want to GET only if exists, CartService would need a getActiveCartOnly method.
        // For now, aligning with getOrCreateCart behavior.
        CartDto cart = cartService.getOrCreateCart(getUserId(principal));
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/items")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add or update item in cart", description = "Adds an item to the cart. If item already exists, its quantity is updated.")
    @ApiResponse(responseCode = "200", description = "Item added/updated in cart successfully")
    public ResponseEntity<CartDto> addItemToCart(Principal principal, @Valid @RequestBody AddItemToCartRequest request) {
        CartDto updatedCart = cartService.addItemToCart(getUserId(principal), request);
        return ResponseEntity.ok(updatedCart);
    }

    @PutMapping("/items/{catalogItemId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update item quantity in cart", description = "Updates the quantity of a specific item in the cart.")
    @ApiResponse(responseCode = "200", description = "Item quantity updated successfully")
    @ApiResponse(responseCode = "404", description = "Item not found in cart")
    public ResponseEntity<CartDto> updateCartItemQuantity(
            Principal principal,
            @Parameter(description = "Catalog Item ID of the item to update") @PathVariable UUID catalogItemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        CartDto updatedCart = cartService.updateCartItemQuantity(getUserId(principal), catalogItemId, request.getNewQuantity());
        return ResponseEntity.ok(updatedCart);
    }

    @DeleteMapping("/items/{catalogItemId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Remove item from cart", description = "Removes a specific item from the cart.")
    @ApiResponse(responseCode = "200", description = "Item removed from cart successfully")
    @ApiResponse(responseCode = "404", description = "Item not found in cart")
    public ResponseEntity<CartDto> removeCartItem(
            Principal principal,
            @Parameter(description = "Catalog Item ID of the item to remove") @PathVariable UUID catalogItemId) {
        CartDto updatedCart = cartService.removeCartItem(getUserId(principal), catalogItemId);
        return ResponseEntity.ok(updatedCart);
    }

    @GetMapping("/total")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get cart totals", description = "Retrieves the calculated totals (subtotal, discount, final total) for the user's active cart.")
    @ApiResponse(responseCode = "200", description = "Cart totals retrieved successfully")
    public ResponseEntity<CartDto> getCartTotals(Principal principal) {
        // CartService.getCartTotals returns the full CartDto which includes totals
        CartDto cartWithTotals = cartService.getCartTotals(getUserId(principal));
        return ResponseEntity.ok(cartWithTotals);
    }

    @PostMapping("/checkout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Checkout cart", description = "Initiates the checkout process for the user's active cart. Cart status changes to CHECKED_OUT and an event is published.")
    @ApiResponse(responseCode = "200", description = "Cart checked out successfully")
    @ApiResponse(responseCode = "400", description = "Cannot checkout (e.g., cart empty)")
    public ResponseEntity<CartDto> checkoutCart(Principal principal) {
        CartDto checkedOutCart = cartService.checkoutCart(getUserId(principal));
        return ResponseEntity.ok(checkedOutCart);
    }
}
