package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateReturnRequest;
import com.sunglassstore.entity.ReturnRequest;
import com.sunglassstore.entity.enums.ReturnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReturnService {
    ReturnRequest createReturn(Long userId, CreateReturnRequest request);
    Page<ReturnRequest> getUserReturns(Long userId, Pageable pageable);
    ReturnRequest getReturnById(Long userId, Long returnId);
    Page<ReturnRequest> getAllReturns(Pageable pageable);
    ReturnRequest updateReturnStatus(Long returnId, ReturnStatus status);
}
