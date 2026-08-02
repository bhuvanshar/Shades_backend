package com.sunglassstore.repository;

import com.sunglassstore.entity.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    @EntityGraph(attributePaths = "variant")
    Page<InventoryMovement> findByVariantVariantIdOrderByCreatedAtDesc(Long variantId, Pageable pageable);
}
