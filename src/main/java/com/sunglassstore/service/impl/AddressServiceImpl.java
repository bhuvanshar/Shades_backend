package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.AddressRequest;
import com.sunglassstore.entity.Address;
import com.sunglassstore.entity.User;
import com.sunglassstore.entity.enums.AddressType;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.AddressRepository;
import com.sunglassstore.service.AddressService;
import com.sunglassstore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final UserService userService;

    @Override
    @Transactional(readOnly = true)
    public List<Address> getUserAddresses(Long userId) {
        return addressRepository.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
    }

    @Override
    @Transactional
    public Address createAddress(Long userId, AddressRequest request) {
        User user = userService.findById(userId);
        Address address = new Address();
        mapRequestToAddress(request, address);
        address.setUser(user);

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            addressRepository.clearDefaultForUser(userId, -1L);
            address.setIsDefault(true);
        }

        return addressRepository.save(address);
    }

    @Override
    @Transactional
    public Address updateAddress(Long userId, Long addressId, AddressRequest request) {
        Address address = addressRepository.findByAddressIdAndUserUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        mapRequestToAddress(request, address);

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            addressRepository.clearDefaultForUser(userId, addressId);
            address.setIsDefault(true);
        }

        return addressRepository.save(address);
    }

    @Override
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        Address address = addressRepository.findByAddressIdAndUserUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        addressRepository.delete(address);
    }

    @Override
    @Transactional
    public Address setDefault(Long userId, Long addressId) {
        Address address = addressRepository.findByAddressIdAndUserUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        addressRepository.clearDefaultForUser(userId, addressId);
        address.setIsDefault(true);
        return addressRepository.save(address);
    }

    private void mapRequestToAddress(AddressRequest request, Address address) {
        address.setAddressType(AddressType.valueOf(request.getAddressType()));
        address.setRecipientName(request.getRecipientName());
        address.setHouseNumber(request.getHouseNumber());
        address.setPhoneNumber(request.getPhoneNumber());
        address.setAddressLine1(request.getAddressLine1());
        address.setAddressLine2(request.getAddressLine2());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setPincode(request.getPincode());
        address.setCountry(request.getCountry());
    }
}
