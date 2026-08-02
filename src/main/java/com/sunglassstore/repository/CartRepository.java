package com.sunglassstore.repository;

import com.sunglassstore.entity.Cart;
import com.sunglassstore.entity.enums.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserUserIdAndCartStatus(Long userId, CartStatus cartStatus);
}
