package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateRefundRequest;
import com.sunglassstore.entity.Refund;

public interface RefundService {
    Refund processRefund(Long paymentId, CreateRefundRequest request);
}
