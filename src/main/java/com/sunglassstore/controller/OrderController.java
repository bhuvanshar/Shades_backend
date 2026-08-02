package com.sunglassstore.controller;

import com.sunglassstore.dto.request.CreateOrderRequest;
import com.sunglassstore.dto.request.UpdateOrderStatusRequest;
import com.sunglassstore.entity.Order;
import com.sunglassstore.entity.enums.OrderStatus;
import com.sunglassstore.dto.response.AdminOrderResponse;
import com.sunglassstore.security.SecurityUser;
import com.sunglassstore.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<Order> createOrder(@AuthenticationPrincipal SecurityUser principal,
                                              @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createOrder(principal.getUserId(), request));
    }

    @GetMapping
    public ResponseEntity<Page<AdminOrderResponse>> getMyOrders(@AuthenticationPrincipal SecurityUser principal,
                                                                 Pageable pageable) {
        return ResponseEntity.ok(orderService.getUserOrdersForCustomer(principal.getUserId(), pageable));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getMyOrder(@AuthenticationPrincipal SecurityUser principal,
                                             @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getUserOrder(principal.getUserId(), orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Order> cancelOrder(@AuthenticationPrincipal SecurityUser principal,
                                              @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(principal.getUserId(), orderId));
    }

    // Admin endpoints
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<Page<AdminOrderResponse>> getAllOrders(Pageable pageable) {
        return ResponseEntity.ok(orderService.getAllOrdersForAdmin(pageable));
    }

    @GetMapping("/admin/{orderId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<AdminOrderResponse> getOrderById(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrderForAdmin(orderId));
    }

    @PatchMapping("/admin/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<AdminOrderResponse> updateOrderStatus(@PathVariable Long orderId,
                                                    @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatusForAdmin(
                orderId, OrderStatus.valueOf(request.getStatus()), request.getNotes()));
    }
}
