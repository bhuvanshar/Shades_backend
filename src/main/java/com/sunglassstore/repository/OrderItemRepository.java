package com.sunglassstore.repository;

import com.sunglassstore.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END " +
           "FROM OrderItem oi WHERE oi.order.user.userId = :userId " +
           "AND oi.variant.product.productId = :productId " +
           "AND oi.order.orderStatus = com.sunglassstore.entity.enums.OrderStatus.DELIVERED")
    boolean hasUserPurchasedProduct(Long userId, Long productId);
}
