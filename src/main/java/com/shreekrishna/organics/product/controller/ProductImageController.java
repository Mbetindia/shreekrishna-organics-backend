
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

    // ADMIN - UPLOAD PRODUCT IMAGE
    @PostMapping(
            value = "/admin/products/{productId}/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ProductImageResponse> uploadImage(
            @PathVariable Long productId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {

        ProductImageResponse response =
                imageService.uploadImage(productId, file);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // PUBLIC - GET PRODUCT IMAGES
    @GetMapping("/products/{productId}/images")
    public ResponseEntity<List<ProductImageResponse>> getImages(
            @PathVariable Long productId
    ) {

        return ResponseEntity.ok(
                imageService.getImages(productId)
        );
    }

    // ADMIN - SET PRIMARY PRODUCT IMAGE
    @PatchMapping(
            "/admin/products/{productId}/images/{imageId}/primary"
    )
    public ResponseEntity<ProductImageResponse> setPrimaryImage(
            @PathVariable Long productId,
            @PathVariable Long imageId
    ) {

        ProductImageResponse response =
                imageService.setPrimaryImage(productId, imageId);

        return ResponseEntity.ok(response);
    }

    // ADMIN - DELETE PRODUCT IMAGE
    @DeleteMapping(
            "/admin/products/{productId}/images/{imageId}"
    )
    public ResponseEntity<Void> deleteImage(
            @PathVariable Long productId,
            @PathVariable Long imageId
    ) {

        imageService.deleteImage(productId, imageId);

        return ResponseEntity.noContent().build();
    }
}
