package com.sunglassstore.service.impl;

import com.sunglassstore.entity.*;
import com.sunglassstore.exception.ConflictException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.ProductRepository;
import com.sunglassstore.repository.WishlistItemRepository;
import com.sunglassstore.repository.WishlistRepository;
import com.sunglassstore.service.UserService;
import com.sunglassstore.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final UserService userService;

    @Override
    @Transactional
    public Wishlist getOrCreateWishlist(Long userId, String name) {
        return wishlistRepository.findByUserUserIdAndWishlistName(userId, name)
                .orElseGet(() -> {
                    User user = userService.findById(userId);
                    Wishlist wishlist = new Wishlist();
                    wishlist.setUser(user);
                    wishlist.setWishlistName(name);
                    return wishlistRepository.save(wishlist);
                });
    }

    @Override
    @Transactional
    public Wishlist addItem(Long userId, Long productId, String wishlistName) {
        Wishlist wishlist = getOrCreateWishlist(userId, wishlistName);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (wishlistItemRepository.existsByWishlistWishlistIdAndProductProductId(
                wishlist.getWishlistId(), productId)) {
            throw new ConflictException("Product is already in this wishlist");
        }

        WishlistItem item = new WishlistItem();
        item.setWishlist(wishlist);
        item.setProduct(product);
        wishlistItemRepository.save(item);

        return wishlistRepository.findById(wishlist.getWishlistId()).orElse(wishlist);
    }

    @Override
    @Transactional
    public Wishlist removeItem(Long userId, Long productId, String wishlistName) {
        Wishlist wishlist = getOrCreateWishlist(userId, wishlistName);
        WishlistItem item = wishlistItemRepository
                .findByWishlistWishlistIdAndProductProductId(wishlist.getWishlistId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found in wishlist"));
        wishlistItemRepository.delete(item);

        return wishlistRepository.findById(wishlist.getWishlistId()).orElse(wishlist);
    }
}
