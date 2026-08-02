package com.sunglassstore.controller;

import com.sunglassstore.entity.InventoryMovement;
import com.sunglassstore.entity.enums.MovementType;
import com.sunglassstore.service.InventoryService;
import com.sunglassstore.dto.response.InventoryMovementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/variants/{variantId}/adjust")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<InventoryMovementResponse> adjustInventory(
            @PathVariable Long variantId,
            @RequestParam Integer quantity,
            @RequestParam String movementType,
            @RequestParam String reason) {
        return ResponseEntity.ok(InventoryMovementResponse.fromEntity(inventoryService.adjustInventory(
                variantId, quantity, MovementType.valueOf(movementType), reason)));
    }

    @GetMapping("/variants/{variantId}/movements")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<Page<InventoryMovementResponse>> getMovements(@PathVariable Long variantId,
                                                                 Pageable pageable) {
        return ResponseEntity.ok(inventoryService.getMovements(variantId, pageable)
                .map(InventoryMovementResponse::fromEntity));
    }
}
