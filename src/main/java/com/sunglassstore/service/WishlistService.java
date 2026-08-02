package com.sunglassstore.service;

import com.sunglassstore.entity.Wishlist;

public interface WishlistService {
    Wishlist getOrCreateWishlist(Long userId, String name);
    Wishlist addItem(Long userId, Long productId, String wishlistName);
    Wishlist removeItem(Long userId, Long productId, String wishlistName);
}
