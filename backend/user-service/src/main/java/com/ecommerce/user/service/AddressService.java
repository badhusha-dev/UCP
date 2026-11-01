package com.ecommerce.user.service;

import com.ecommerce.user.model.CustomerAddress;
import com.ecommerce.user.repository.CustomerAddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddressService {

    private final CustomerAddressRepository addressRepository;

    public List<CustomerAddress> getUserAddresses(Long userId) {
        return addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
    }

    @Transactional
    public CustomerAddress addAddress(Long userId, CustomerAddress address) {
        validate(address);
        address.setUserId(userId);
        if (address.getCreatedAt() == null) {
            address.setCreatedAt(OffsetDateTime.now());
        }
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            // Unset previous defaults
            List<CustomerAddress> existing = addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
            for (CustomerAddress addr : existing) {
                if (Boolean.TRUE.equals(addr.getIsDefault())) {
                    addr.setIsDefault(false);
                    addressRepository.save(addr);
                }
            }
        }
        return addressRepository.save(address);
    }

    @Transactional
    public CustomerAddress updateAddress(Long userId, Long addressId, CustomerAddress request) {
        CustomerAddress address = addressRepository.findById(addressId)
                .filter(existing -> existing.getUserId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found."));
        validate(request);
        address.setAddressType(request.getAddressType());
        address.setStreet(request.getStreet().trim());
        address.setCity(request.getCity().trim());
        address.setState(blankToNull(request.getState()));
        address.setPostalCode(request.getPostalCode().trim());
        address.setCountry(request.getCountry().trim());
        address.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));
        if (Boolean.TRUE.equals(address.getIsDefault())) clearOtherDefaults(userId, addressId);
        return addressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        CustomerAddress address = addressRepository.findById(addressId)
                .filter(existing -> existing.getUserId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found."));
        addressRepository.delete(address);
    }

    private void clearOtherDefaults(Long userId, Long exceptId) {
        for (CustomerAddress existing : addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)) {
            if (!existing.getId().equals(exceptId) && Boolean.TRUE.equals(existing.getIsDefault())) {
                existing.setIsDefault(false);
                addressRepository.save(existing);
            }
        }
    }

    private void validate(CustomerAddress address) {
        if (address.getStreet() == null || address.getStreet().isBlank() || address.getCity() == null || address.getCity().isBlank()
                || address.getPostalCode() == null || address.getPostalCode().isBlank() || address.getCountry() == null || address.getCountry().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Street, city, postal code, and country are required.");
        }
        String type = address.getAddressType() == null ? "SHIPPING" : address.getAddressType().trim().toUpperCase();
        if (!type.equals("SHIPPING") && !type.equals("BILLING")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Address type must be SHIPPING or BILLING.");
        address.setAddressType(type);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
