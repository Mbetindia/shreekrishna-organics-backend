package com.shreekrishna.organics.video.dto;

import jakarta.validation.constraints.*;

public record VideoRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 1000) String youtubeUrl,
        @Size(max = 10000) String description,
        Boolean active,
        @Min(0) Integer displayOrder
) {}
