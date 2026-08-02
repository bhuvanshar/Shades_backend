package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CreateShipmentRequest;
import com.sunglassstore.entity.Order;
import com.sunglassstore.entity.Shipment;
import com.sunglassstore.entity.enums.OrderStatus;
import com.sunglassstore.entity.enums.ShipmentStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.OrderRepository;
import com.sunglassstore.repository.ShipmentRepository;
import com.sunglassstore.service.OrderService;
import com.sunglassstore.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ShipmentServiceImpl implements ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Override
    @Transactional
    public Shipment createShipment(Long orderId, CreateShipmentRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getOrderStatus() != OrderStatus.CONFIRMED &&
                order.getOrderStatus() != OrderStatus.PROCESSING) {
            throw new BadRequestException("Shipment can only be created for CONFIRMED or PROCESSING orders");
        }

        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setShippingProvider(request.getShippingProvider());
        shipment.setTrackingNumber(request.getTrackingNumber());
        shipment.setShipmentStatus(ShipmentStatus.PENDING);

        orderService.updateOrderStatus(orderId, OrderStatus.PROCESSING, "Shipment created");

        return shipmentRepository.save(shipment);
    }

    @Override
    @Transactional
    public Shipment updateShipmentStatus(Long shipmentId, ShipmentStatus status) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found"));

        shipment.setShipmentStatus(status);

        if (status == ShipmentStatus.SHIPPED) {
            shipment.setShippedAt(LocalDateTime.now());
            orderService.updateOrderStatus(shipment.getOrder().getOrderId(),
                    OrderStatus.SHIPPED, "Order shipped");
        } else if (status == ShipmentStatus.DELIVERED) {
            shipment.setDeliveredAt(LocalDateTime.now());
            orderService.updateOrderStatus(shipment.getOrder().getOrderId(),
                    OrderStatus.DELIVERED, "Order delivered");
        }

        return shipmentRepository.save(shipment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Shipment> getShipments(Long orderId, Pageable pageable) {
        return shipmentRepository.findByOrderOrderId(orderId, pageable);
    }
}
