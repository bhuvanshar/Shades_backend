package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CreateReturnRequest;
import com.sunglassstore.entity.*;
import com.sunglassstore.entity.enums.MovementType;
import com.sunglassstore.entity.enums.OrderStatus;
import com.sunglassstore.entity.enums.ReturnStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.*;
import com.sunglassstore.service.ReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReturnServiceImpl implements ReturnService {

    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnItemRepository returnItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryMovementRepository inventoryMovementRepository;

    @Override
    @Transactional
    public ReturnRequest createReturn(Long userId, CreateReturnRequest request) {
        Order order = orderRepository.findByOrderIdAndUserUserId(request.getOrderId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getOrderStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException("Returns can only be requested for delivered orders");
        }

        ReturnRequest returnRequest = new ReturnRequest();
        returnRequest.setOrder(order);
        returnRequest.setUser(order.getUser());
        returnRequest.setReturnStatus(ReturnStatus.REQUESTED);
        returnRequest.setReturnReason(request.getReturnReason());

        List<ReturnItem> returnItems = new ArrayList<>();
        for (CreateReturnRequest.ReturnItemRequest itemReq : request.getItems()) {
            OrderItem orderItem = orderItemRepository.findById(itemReq.getOrderItemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Order item not found: " + itemReq.getOrderItemId()));

            // Verify the order item belongs to this order
            if (!orderItem.getOrder().getOrderId().equals(order.getOrderId())) {
                throw new BadRequestException("Order item does not belong to this order");
            }

            // Validate return quantity
            Integer alreadyReturned = returnItemRepository
                    .sumReturnedQuantityByOrderItemId(orderItem.getOrderItemId());
            int previouslyReturned = alreadyReturned != null ? alreadyReturned : 0;
            int maxReturnable = orderItem.getQuantity() - previouslyReturned;

            if (itemReq.getQuantity() > maxReturnable) {
                throw new BadRequestException(
                        "Cannot return " + itemReq.getQuantity() + " units of "
                                + orderItem.getProductName() + ". Maximum returnable: " + maxReturnable);
            }

            ReturnItem returnItem = new ReturnItem();
            returnItem.setReturnRequest(returnRequest);
            returnItem.setOrderItem(orderItem);
            returnItem.setQuantity(itemReq.getQuantity());
            returnItem.setReturnReason(itemReq.getReturnReason());
            returnItems.add(returnItem);
        }

        returnRequest.setItems(returnItems);
        return returnRequestRepository.save(returnRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReturnRequest> getUserReturns(Long userId, Pageable pageable) {
        return returnRequestRepository.findByUserUserIdOrderByRequestedAtDesc(userId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnRequest getReturnById(Long userId, Long returnId) {
        ReturnRequest returnRequest = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return request not found"));
        if (!returnRequest.getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Return request not found");
        }
        return returnRequest;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReturnRequest> getAllReturns(Pageable pageable) {
        return returnRequestRepository.findAllByOrderByRequestedAtDesc(pageable);
    }

    @Override
    @Transactional
    public ReturnRequest updateReturnStatus(Long returnId, ReturnStatus status) {
        ReturnRequest returnRequest = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return request not found"));

        returnRequest.setReturnStatus(status);

        // When return is received/completed, restore inventory
        if (status == ReturnStatus.RECEIVED || status == ReturnStatus.COMPLETED) {
            for (ReturnItem item : returnRequest.getItems()) {
                ProductVariant lockedVariant = productVariantRepository.findByIdForUpdate(
                        item.getOrderItem().getVariant().getVariantId())
                        .orElseThrow(() -> new BadRequestException("Variant not found"));

                lockedVariant.setQuantityAvailable(lockedVariant.getQuantityAvailable() + item.getQuantity());
                productVariantRepository.save(lockedVariant);

                InventoryMovement movement = new InventoryMovement();
                movement.setVariant(lockedVariant);
                movement.setMovementType(MovementType.RETURN);
                movement.setQuantityChange(item.getQuantity());
                movement.setReferenceId(returnRequest.getReturnId());
                movement.setNotes("Return #" + returnRequest.getReturnId() + " received");
                inventoryMovementRepository.save(movement);
            }
        }

        return returnRequestRepository.save(returnRequest);
    }
}
