package com.shreekrishna.organics.address.service;

import com.shreekrishna.organics.address.dto.AddressRequest;
import com.shreekrishna.organics.address.dto.AddressResponse;
import com.shreekrishna.organics.address.entity.Address;
import com.shreekrishna.organics.address.repository.AddressRepository;
import com.shreekrishna.organics.exception.ResourceNotFoundException;
import com.shreekrishna.organics.user.entity.User;
import com.shreekrishna.organics.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressService(AddressRepository addressRepository, UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(String username) {
        User user = getCurrentUser(username);
        return addressRepository.findAllByUserIdOrderByDefaultAddressDescIdDesc(user.getId())
                .stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public AddressResponse createAddress(String username, AddressRequest request) {
        User user = getCurrentUser(username);
        boolean firstAddress = addressRepository.countByUserId(user.getId()) == 0;
        boolean shouldBeDefault = firstAddress || request.defaultAddress();

        if (shouldBeDefault) {
            addressRepository.clearDefaultAddress(user.getId());
        }

        Address address = Address.builder()
                .user(user)
                .fullName(request.fullName().trim())
                .mobile(request.mobile().trim())
                .addressLine1(request.addressLine1().trim())
                .addressLine2(normalizeOptional(request.addressLine2()))
                .landmark(normalizeOptional(request.landmark()))
                .city(request.city().trim())
                .state(request.state().trim())
                .postalCode(request.postalCode().trim())
                .country(request.country().trim())
                .addressType(request.addressType())
                .defaultAddress(shouldBeDefault)
                .build();

        return mapToResponse(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse updateAddress(String username, Long addressId, AddressRequest request) {
        User user = getCurrentUser(username);
        Address address = getOwnedAddress(addressId, user.getId());

        if (request.defaultAddress() && !address.isDefaultAddress()) {
            addressRepository.clearDefaultAddress(user.getId());
        }

        address.setFullName(request.fullName().trim());
        address.setMobile(request.mobile().trim());
        address.setAddressLine1(request.addressLine1().trim());
        address.setAddressLine2(normalizeOptional(request.addressLine2()));
        address.setLandmark(normalizeOptional(request.landmark()));
        address.setCity(request.city().trim());
        address.setState(request.state().trim());
        address.setPostalCode(request.postalCode().trim());
        address.setCountry(request.country().trim());
        address.setAddressType(request.addressType());

        // Preserve an existing default address if the request sends false.
        if (!address.isDefaultAddress()) {
            address.setDefaultAddress(request.defaultAddress());
        }

        return mapToResponse(addressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(String username, Long addressId) {
        User user = getCurrentUser(username);
        Address address = getOwnedAddress(addressId, user.getId());
        boolean wasDefault = address.isDefaultAddress();

        addressRepository.delete(address);
        addressRepository.flush();

        if (wasDefault) {
            List<Address> remaining = addressRepository
                    .findAllByUserIdOrderByDefaultAddressDescIdDesc(user.getId());
            if (!remaining.isEmpty()) {
                Address newDefault = remaining.get(0);
                newDefault.setDefaultAddress(true);
                addressRepository.save(newDefault);
            }
        }
    }

    private User getCurrentUser(String username) {
        if (username == null || username.isBlank()) {
            throw new ResourceNotFoundException("Authenticated user could not be found");
        }

        String identity = username.trim();
        if (identity.contains("@")) {
            return userRepository.findByEmail(identity.toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Authenticated user could not be found"));
        }

        return userRepository.findByMobile(identity)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user could not be found"));
    }

    private Address getOwnedAddress(Long addressId, Long userId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
    }

    private AddressResponse mapToResponse(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getFullName(),
                address.getMobile(),
                address.getAddressLine1(),
                address.getAddressLine2(),
                address.getLandmark(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.getAddressType(),
                address.isDefaultAddress()
        );
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
