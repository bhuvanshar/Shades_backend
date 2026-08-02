package com.sunglassstore.service;

import com.sunglassstore.dto.response.AdminCustomerResponse;
import com.sunglassstore.entity.User;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.repository.AddressRepository;
import com.sunglassstore.repository.OrderRepository;
import com.sunglassstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCustomerService {
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;

    @Transactional(readOnly = true)
    public Page<AdminCustomerResponse> getCustomers(Pageable pageable) {
        return userRepository.findCustomers(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AdminCustomerResponse getCustomer(Long userId) {
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        rejectAdministrator(user);
        return toResponse(user);
    }

    @Transactional
    public AdminCustomerResponse setActive(Long userId, boolean active) {
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        rejectAdministrator(user);
        user.setIsActive(active);
        return toResponse(userRepository.save(user));
    }

    private AdminCustomerResponse toResponse(User user) {
        return AdminCustomerResponse.fromEntity(user, orderRepository.countByUserUserId(user.getUserId()),
                orderRepository.sumCompletedValueByUserId(user.getUserId()),
                orderRepository.findLastOrderAtByUserId(user.getUserId()),
                addressRepository.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(user.getUserId()));
    }

    private void rejectAdministrator(User user) {
        if (user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getRoleName()))) {
            throw new BadRequestException("Administrator accounts cannot be managed as customers");
        }
    }
}
