package com.sunglassstore.controller;

import com.sunglassstore.dto.request.CreateReturnRequest;
import com.sunglassstore.dto.request.UpdateReturnStatusRequest;
import com.sunglassstore.entity.ReturnRequest;
import com.sunglassstore.entity.enums.ReturnStatus;
import com.sunglassstore.security.SecurityUser;
import com.sunglassstore.service.ReturnService;
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
@RequestMapping("/api/returns")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;

    @PostMapping
    public ResponseEntity<ReturnRequest> createReturn(@AuthenticationPrincipal SecurityUser principal,
                                                       @Valid @RequestBody CreateReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(returnService.createReturn(principal.getUserId(), request));
    }

    @GetMapping
    public ResponseEntity<Page<ReturnRequest>> getMyReturns(@AuthenticationPrincipal SecurityUser principal,
                                                             Pageable pageable) {
        return ResponseEntity.ok(returnService.getUserReturns(principal.getUserId(), pageable));
    }

    @GetMapping("/{returnId}")
    public ResponseEntity<ReturnRequest> getReturn(@AuthenticationPrincipal SecurityUser principal,
                                                    @PathVariable Long returnId) {
        return ResponseEntity.ok(returnService.getReturnById(principal.getUserId(), returnId));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<Page<ReturnRequest>> getAllReturns(Pageable pageable) {
        return ResponseEntity.ok(returnService.getAllReturns(pageable));
    }

    @PatchMapping("/admin/{returnId}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<ReturnRequest> updateReturnStatus(@PathVariable Long returnId,
                                                             @Valid @RequestBody UpdateReturnStatusRequest request) {
        return ResponseEntity.ok(returnService.updateReturnStatus(
                returnId, ReturnStatus.valueOf(request.getStatus())));
    }
}
