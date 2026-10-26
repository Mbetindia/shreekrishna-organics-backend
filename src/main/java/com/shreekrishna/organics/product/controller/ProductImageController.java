package com.shreekrishna.organics.product.controller;

import com.shreekrishna.organics.product.dto.ProductImageResponse;
import com.shreekrishna.organics.product.service.ProductImageService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ProductImageController {

    private final ProductImageService imageService;

    public ProductImageController(
            ProductImageService imageService
    ) {
        this.imageService = imageService;
    }

    // =========================================================
    // ADMIN - UPLOAD NORMAL OR VARIANT PRODUCT IMAGE
    // =========================================================

    /*
     * NORMAL PRODUCT IMAGE:
     *
     * POST /api/v1/admin/products/{productId}/images
     *
     * FormData:
     * file = image.jpg
     *
     * ---------------------------------------------------------
     *
     * VARIANT-SPECIFIC IMAGE:
     *
     * POST /api/v1/admin/products/{productId}/images
     *
     * FormData:
     * file = image.jpg
     * variantId = 21
     *
     * ---------------------------------------------------------
     *
     * If variantId is not provided:
     *     Upload normal product image.
     *
     * If variantId is provided:
     *     Upload or replace that variant's image.
     */

    @PostMapping(
            value = "/admin/products/{productId}/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ProductImageResponse> uploadImage(
            @PathVariable Long productId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(
                    value = "variantId",
                    required = false
            ) Long variantId
    ) throws IOException {

        ProductImageResponse response =
                imageService.uploadImage(
                        productId,
                        file,
                        variantId
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // =========================================================
    // PUBLIC - GET ALL PRODUCT IMAGES
    // =========================================================

    /*
     * Returns:
     * - Normal primary image
     * - Normal gallery images
     * - Variant-specific images
     *
     * Each image includes variantId.
     *
     * variantId = null:
     *     Normal product image.
     *
     * variantId != null:
     *     Variant-specific image.
     */

    @GetMapping("/products/{productId}/images")
    public ResponseEntity<List<ProductImageResponse>> getImages(
            @PathVariable Long productId
    ) {

        return ResponseEntity.ok(
                imageService.getImages(productId)
        );
    }

    // =========================================================
    // ADMIN - SET PRIMARY PRODUCT IMAGE
    // =========================================================

    /*
     * Only normal product images can be primary.
     *
     * Variant-specific images are rejected
     * by ProductImageService.
     */

    @PatchMapping(
            "/admin/products/{productId}/images/{imageId}/primary"
    )
    public ResponseEntity<ProductImageResponse> setPrimaryImage(
            @PathVariable Long productId,
            @PathVariable Long imageId
    ) {

        ProductImageResponse response =
                imageService.setPrimaryImage(
                        productId,
                        imageId
                );

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // ADMIN - DELETE NORMAL OR VARIANT PRODUCT IMAGE
    // =========================================================

    /*
     * Works for:
     * - Normal primary images
     * - Normal gallery images
     * - Variant-specific images
     *
     * Cloudinary cleanup is handled by ProductImageService.
     */

    @DeleteMapping(
            "/admin/products/{productId}/images/{imageId}"
    )
    public ResponseEntity<Void> deleteImage(
            @PathVariable Long productId,
            @PathVariable Long imageId
    ) {

        imageService.deleteImage(
                productId,
                imageId
        );

        return ResponseEntity.noContent().build();
    }
}