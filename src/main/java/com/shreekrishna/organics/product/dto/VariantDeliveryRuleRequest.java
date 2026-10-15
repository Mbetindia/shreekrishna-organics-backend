
package com.shreekrishna.organics.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record VariantDeliveryRuleRequest(

        @NotNull(message = "Minimum quantity is required")
        @Min(value = 1, message = "Minimum quantity must be at least 1")
        Integer minQuantity,

        @Min(value = 1, message = "Maximum quantity must be at least 1")
        Integer maxQuantity,

        @NotNull(message = "Delivery charge is required")
        @DecimalMin(
                value = "0.00",
                message = "Delivery charge cannot be negative"
        )
        @Digits(
                integer = 10,
                fraction = 2,
                message = "Delivery charge must have at most 2 decimal places"
        )
        BigDecimal deliveryCharge

) {
}
