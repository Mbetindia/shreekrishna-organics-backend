package com.shreekrishna.organics.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlogRequest(

        @NotBlank(message = "Blog title is required")
        @Size(max = 255, message = "Title must be 255 characters or less")
        String title,

        @NotBlank(message = "Blog content is required")
        String content,

        @Size(max = 2048, message = "Image URL is too long")
        String imageUrl,

        @Size(max = 255, message = "Image Public ID is too long")
        String imagePublicId

) {
}