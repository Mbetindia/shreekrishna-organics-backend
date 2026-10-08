package com.shreekrishna.organics.address.controller;

import com.shreekrishna.organics.address.dto.AddressRequest;
import com.shreekrishna.organics.address.dto.AddressResponse;
import com.shreekrishna.organics.address.service.AddressService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(
            AddressService addressService) {

        this.addressService = addressService;
    }

    @GetMapping
    public ResponseEntity<List<AddressResponse>> getAddresses(
            Authentication authentication) {

        return ResponseEntity.ok(
                addressService.getAddresses(
                        authentication.getName()
                )
        );
    }

    @PostMapping
    public ResponseEntity<AddressResponse> createAddress(
            Authentication authentication,
            @Valid @RequestBody AddressRequest request) {

        AddressResponse response =
                addressService.createAddress(
                        authentication.getName(),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AddressResponse> updateAddress(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request) {

        AddressResponse response =
                addressService.updateAddress(
                        authentication.getName(),
                        id,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(
            Authentication authentication,
            @PathVariable Long id) {

        addressService.deleteAddress(
                authentication.getName(),
                id
        );

        return ResponseEntity.noContent().build();
    }
}