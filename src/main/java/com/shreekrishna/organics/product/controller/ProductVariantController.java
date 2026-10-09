
package com.shreekrishna.organics.product.controller;

import com.shreekrishna.organics.product.dto.ProductVariantRequest;
import com.shreekrishna.organics.product.dto.ProductVariantResponse;
import com.shreekrishna.organics.product.service.ProductVariantService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ProductVariantController {

    private final ProductVariantService variantService;

    public ProductVariantController(
            ProductVariantService variantService) {
        this.variantService = variantService;
    }

    // Create a new product variant
    @PostMapping("/admin/product-variants")
    public ResponseEntity<ProductVariantResponse> create(
            @Valid @RequestBody ProductVariantRequest request) {

        ProductVariantResponse response =
                variantService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // Get variant by ID
    @GetMapping("/product-variants/{id}")
    public ResponseEntity<ProductVariantResponse> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(variantService.getById(id));
    }

    // Get all variants for a product
    @GetMapping("/products/{productId}/variants")
    public ResponseEntity<List<ProductVariantResponse>> getByProduct(
            @PathVariable Long productId) {

        return ResponseEntity.ok(
                variantService.getByProduct(productId)
        );
    }

    // Update a product variant
    @PutMapping("/admin/product-variants/{id}")
    public ResponseEntity<ProductVariantResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductVariantRequest request) {

        return ResponseEntity.ok(
                variantService.update(id, request)
        );
    }

    // Soft delete a product variant
    @DeleteMapping("/admin/product-variants/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id) {

        variantService.softDelete(id);

        return ResponseEntity.noContent().build();
    }
}
