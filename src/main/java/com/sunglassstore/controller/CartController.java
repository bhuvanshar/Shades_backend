package com.sunglassstore.controller;

import com.sunglassstore.dto.request.CartItemRequest;
import com.sunglassstore.entity.Cart;
import com.sunglassstore.security.SecurityUser;
import com.sunglassstore.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<Cart> getCart(@AuthenticationPrincipal SecurityUser principal) {
        return ResponseEntity.ok(cartService.getOrCreateCart(principal.getUserId()));
    }

    @PostMapping("/items")
    public ResponseEntity<Cart> addItem(@AuthenticationPrincipal SecurityUser principal,
                                         @Valid @RequestBody CartItemRequest request) {
        return ResponseEntity.ok(cartService.addItem(principal.getUserId(), request));
    }

    @PutMapping("/items/{variantId}")
    public ResponseEntity<Cart> updateItem(@AuthenticationPrincipal SecurityUser principal,
                                            @PathVariable Long variantId,
                                            @RequestParam Integer quantity) {
        return ResponseEntity.ok(cartService.updateItemQuantity(principal.getUserId(), variantId, quantity));
    }

    @DeleteMapping("/items/{variantId}")
    public ResponseEntity<Cart> removeItem(@AuthenticationPrincipal SecurityUser principal,
                                            @PathVariable Long variantId) {
        return ResponseEntity.ok(cartService.removeItem(principal.getUserId(), variantId));
    }

    @DeleteMapping
    public ResponseEntity<Cart> clearCart(@AuthenticationPrincipal SecurityUser principal) {
        return ResponseEntity.ok(cartService.clearCart(principal.getUserId()));
    }
}
