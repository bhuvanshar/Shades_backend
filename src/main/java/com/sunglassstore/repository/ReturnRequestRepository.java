package com.sunglassstore.repository;

import com.sunglassstore.entity.ReturnRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    Page<ReturnRequest> findByUserUserIdOrderByRequestedAtDesc(Long userId, Pageable pageable);

    Page<ReturnRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);
}
