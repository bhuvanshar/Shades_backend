package com.sunglassstore.service;

import com.sunglassstore.dto.request.CartItemRequest;
import com.sunglassstore.entity.Cart;

public interface CartService {
    Cart getOrCreateCart(Long userId);
    Cart addItem(Long userId, CartItemRequest request);
    Cart updateItemQuantity(Long userId, Long variantId, Integer quantity);
    Cart removeItem(Long userId, Long variantId);
    Cart clearCart(Long userId);
}
