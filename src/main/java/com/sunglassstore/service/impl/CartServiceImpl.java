package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CartItemRequest;
import com.sunglassstore.entity.Cart;
import com.sunglassstore.entity.CartItem;
import com.sunglassstore.entity.ProductVariant;
import com.sunglassstore.entity.User;
import com.sunglassstore.entity.enums.CartStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.InsufficientInventoryException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.CartItemRepository;
import com.sunglassstore.repository.CartRepository;
import com.sunglassstore.repository.ProductVariantRepository;
import com.sunglassstore.service.CartService;
import com.sunglassstore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final UserService userService;

    @Override
    @Transactional
    public Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserUserIdAndCartStatus(userId, CartStatus.ACTIVE)
                .orElseGet(() -> {
                    User user = userService.findById(userId);
                    Cart cart = new Cart();
                    cart.setUser(user);
                    cart.setCartStatus(CartStatus.ACTIVE);
                    return cartRepository.save(cart);
                });
    }

    @Override
    @Transactional
    public Cart addItem(Long userId, CartItemRequest request) {
        Cart cart = getOrCreateCart(userId);
        ProductVariant variant = validateVariant(request.getVariantId(), request.getQuantity());

        Optional<CartItem> existing = cartItemRepository
                .findByCartCartIdAndVariantVariantId(cart.getCartId(), request.getVariantId());

        if (existing.isPresent()) {
            CartItem item = existing.get();
            int newQuantity = item.getQuantity() + request.getQuantity();
            validateInventory(variant, newQuantity);
            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
        } else {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setVariant(variant);
            item.setQuantity(request.getQuantity());
            cartItemRepository.save(item);
        }

        return cartRepository.findById(cart.getCartId()).orElse(cart);
    }

    @Override
    @Transactional
    public Cart updateItemQuantity(Long userId, Long variantId, Integer quantity) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository.findByCartCartIdAndVariantVariantId(cart.getCartId(), variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found in cart"));

        if (quantity <= 0) {
            cartItemRepository.delete(item);
        } else {
            validateInventory(item.getVariant(), quantity);
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }

        return cartRepository.findById(cart.getCartId()).orElse(cart);
    }

    @Override
    @Transactional
    public Cart removeItem(Long userId, Long variantId) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository.findByCartCartIdAndVariantVariantId(cart.getCartId(), variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found in cart"));
        cartItemRepository.delete(item);
        return cartRepository.findById(cart.getCartId()).orElse(cart);
    }

    @Override
    @Transactional
    public Cart clearCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        cart.getItems().clear();
        return cartRepository.save(cart);
    }

    private ProductVariant validateVariant(Long variantId, int quantity) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));

        if (!Boolean.TRUE.equals(variant.getProduct().getIsActive())) {
            throw new BadRequestException("This product is no longer available");
        }

        validateInventory(variant, quantity);
        return variant;
    }

    private void validateInventory(ProductVariant variant, int quantity) {
        if (variant.getQuantityAvailable() < quantity) {
            throw new InsufficientInventoryException(
                    "Only " + variant.getQuantityAvailable() + " units available for " + variant.getSku());
        }
    }
}
