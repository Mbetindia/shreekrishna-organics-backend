package com.shreekrishna.organics.testimonial.dto;

import jakarta.validation.constraints.*;

public record TestimonialRequest(
    @NotBlank @Size(max = 120) String customerName,
    @NotBlank @Size(max = 2000) String comment,
    @NotNull @Min(1) @Max(5) Integer rating,
    @NotNull Boolean active
) {}
