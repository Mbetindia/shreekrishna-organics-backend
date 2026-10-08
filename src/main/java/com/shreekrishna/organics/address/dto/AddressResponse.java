package com.shreekrishna.organics.address.dto;

import com.shreekrishna.organics.address.entity.AddressType;

public record AddressResponse(

        Long id,
        String fullName,
        String mobile,
        String addressLine1,
        String addressLine2,
        String landmark,
        String city,
        String state,
        String postalCode,
        String country,
        AddressType addressType,
        boolean defaultAddress

) {
}