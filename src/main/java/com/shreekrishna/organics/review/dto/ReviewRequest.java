
package com.shreekrishna.organics.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(

        @NotNull(message = "Product ID is required")
        Long productId,

        @NotBlank(message = "Customer name is required")
        @Size(max = 150, message = "Customer name cannot exceed 150 characters")
        String customerName,

        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Minimum rating is 1")
        @Max(value = 5, message = "Maximum rating is 5")
        Integer rating,

        String comment
) {
}
