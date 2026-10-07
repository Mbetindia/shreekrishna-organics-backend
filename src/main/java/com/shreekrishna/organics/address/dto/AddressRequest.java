package com.shreekrishna.organics.address.dto;

import com.shreekrishna.organics.address.entity.AddressType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @NotBlank(message = "Mobile number is required")
        @Pattern(
                regexp = "^[6-9][0-9]{9}$",
                message = "Please provide a valid 10-digit mobile number"
        )
        String mobile,

        @NotBlank(message = "Address line 1 is required")
        @Size(max = 255)
        String addressLine1,

        @Size(max = 255)
        String addressLine2,

        @Size(max = 255)
        String landmark,

        @NotBlank(message = "City is required")
        @Size(max = 100)
        String city,

        @NotBlank(message = "State is required")
        @Size(max = 100)
        String state,

        @NotBlank(message = "Postal code is required")
        @Pattern(
                regexp = "^[1-9][0-9]{5}$",
                message = "Please provide a valid 6-digit postal code"
        )
        String postalCode,

        @NotBlank(message = "Country is required")
        @Size(max = 100)
        String country,

        @NotNull(message = "Address type is required")
        AddressType addressType,

        boolean defaultAddress

) {
}