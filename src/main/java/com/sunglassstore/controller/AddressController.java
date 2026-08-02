package com.sunglassstore.controller;

import com.sunglassstore.dto.request.AddressRequest;
import com.sunglassstore.entity.Address;
import com.sunglassstore.security.SecurityUser;
import com.sunglassstore.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public ResponseEntity<List<Address>> getAddresses(@AuthenticationPrincipal SecurityUser principal) {
        return ResponseEntity.ok(addressService.getUserAddresses(principal.getUserId()));
    }

    @PostMapping
    public ResponseEntity<Address> createAddress(@AuthenticationPrincipal SecurityUser principal,
                                                  @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(addressService.createAddress(principal.getUserId(), request));
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<Address> updateAddress(@AuthenticationPrincipal SecurityUser principal,
                                                  @PathVariable Long addressId,
                                                  @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(addressService.updateAddress(principal.getUserId(), addressId, request));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal SecurityUser principal,
                                               @PathVariable Long addressId) {
        addressService.deleteAddress(principal.getUserId(), addressId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{addressId}/default")
    public ResponseEntity<Address> setDefault(@AuthenticationPrincipal SecurityUser principal,
                                               @PathVariable Long addressId) {
        return ResponseEntity.ok(addressService.setDefault(principal.getUserId(), addressId));
    }
}
