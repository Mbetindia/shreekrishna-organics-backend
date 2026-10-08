package com.shreekrishna.organics.category.controller;

import com.shreekrishna.organics.category.dto.CategoryRequest;
import com.shreekrishna.organics.category.dto.CategoryResponse;
import com.shreekrishna.organics.category.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    // =========================
    // PUBLIC APIs
    // =========================

    // Get all active categories
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {

        return ResponseEntity.ok(
                categoryService.getAllCategories()
        );
    }

    // Get category by ID
    @GetMapping("/categories/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(
                categoryService.getCategoryById(id)
        );
    }

    // =========================
    // ADMIN APIs
    // =========================

    // Create category
    @PostMapping("/admin/categories")
    public ResponseEntity<CategoryResponse> createCategory(
            @RequestBody CategoryRequest request
    ) {

        CategoryResponse response =
                categoryService.createCategory(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // Update category
    @PutMapping("/admin/categories/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @RequestBody CategoryRequest request
    ) {

        return ResponseEntity.ok(
                categoryService.updateCategory(id, request)
        );
    }

    // Deactivate category
    @DeleteMapping("/admin/categories/{id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long id
    ) {

        categoryService.deleteCategory(id);

        return ResponseEntity.noContent().build();
    }
}