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

@Service
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressService(
            AddressRepository addressRepository,
            UserRepository userRepository) {

        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(String email) {

        User user = getCurrentUser(email);

        return addressRepository
                .findAllByUserIdOrderByDefaultAddressDescIdDesc(
                        user.getId()
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public AddressResponse createAddress(
            String email,
            AddressRequest request) {

        User user = getCurrentUser(email);

        /*
         * Make the first address default automatically.
         */
        boolean firstAddress =
                addressRepository.countByUserId(user.getId()) == 0;

        boolean shouldBeDefault =
                firstAddress || request.defaultAddress();

        if (shouldBeDefault) {
            addressRepository.clearDefaultAddress(user.getId());
        }

        Address address = Address.builder()
                .user(user)
                .fullName(request.fullName().trim())
                .mobile(request.mobile().trim())
                .addressLine1(request.addressLine1().trim())
                .addressLine2(
                        normalizeOptional(request.addressLine2())
                )
                .landmark(
                        normalizeOptional(request.landmark())
                )
                .city(request.city().trim())
                .state(request.state().trim())
                .postalCode(request.postalCode().trim())
                .country(request.country().trim())
                .addressType(request.addressType())
                .defaultAddress(shouldBeDefault)
                .build();

        Address savedAddress =
                addressRepository.save(address);

        return mapToResponse(savedAddress);
    }

    @Transactional
    public AddressResponse updateAddress(
            String email,
            Long addressId,
            AddressRequest request) {

        User user = getCurrentUser(email);

        Address address = getOwnedAddress(
                addressId,
                user.getId()
        );

        if (request.defaultAddress()
                && !address.isDefaultAddress()) {

            addressRepository.clearDefaultAddress(
                    user.getId()
            );
        }

        address.setFullName(
                request.fullName().trim()
        );

        address.setMobile(
                request.mobile().trim()
        );

        address.setAddressLine1(
                request.addressLine1().trim()
        );

        address.setAddressLine2(
                normalizeOptional(
                        request.addressLine2()
                )
        );

        address.setLandmark(
                normalizeOptional(
                        request.landmark()
                )
        );

        address.setCity(
                request.city().trim()
        );

        address.setState(
                request.state().trim()
        );

        address.setPostalCode(
                request.postalCode().trim()
        );

        address.setCountry(
                request.country().trim()
        );

        address.setAddressType(
                request.addressType()
        );

        /*
         * Don't allow the only/default address to accidentally
         * leave the user with no default address.
         *
         * If this address is currently default and the request
         * sends false, keep it default.
         */
        if (!address.isDefaultAddress()) {
            address.setDefaultAddress(
                    request.defaultAddress()
            );
        }

        Address savedAddress =
                addressRepository.save(address);

        return mapToResponse(savedAddress);
    }

    @Transactional
    public void deleteAddress(
            String email,
            Long addressId) {

        User user = getCurrentUser(email);

        Address address = getOwnedAddress(
                addressId,
                user.getId()
        );

        boolean wasDefault =
                address.isDefaultAddress();

        addressRepository.delete(address);
        addressRepository.flush();

        /*
         * If default address was deleted,
         * automatically select another address.
         */
        if (wasDefault) {

            List<Address> remaining =
                    addressRepository
                            .findAllByUserIdOrderByDefaultAddressDescIdDesc(
                                    user.getId()
                            );

            if (!remaining.isEmpty()) {

                Address newDefault = remaining.get(0);

                newDefault.setDefaultAddress(true);

                addressRepository.save(newDefault);
            }
        }
    }

    private User getCurrentUser(String email) {

        if (email == null || email.isBlank()) {
            throw new ResourceNotFoundException(
                    "Authenticated user could not be found"
            );
        }

        return userRepository
                .findByEmail(
                        email.trim().toLowerCase()
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Authenticated user could not be found"
                        )
                );
    }

    private Address getOwnedAddress(
            Long addressId,
            Long userId) {

        return addressRepository
                .findByIdAndUserId(
                        addressId,
                        userId
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Address not found"
                        )
                );
    }

    private AddressResponse mapToResponse(
            Address address) {

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

    private String normalizeOptional(
            String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}