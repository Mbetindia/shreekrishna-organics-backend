package com.shreekrishna.organics.category.service;

import com.shreekrishna.organics.category.dto.CategoryRequest;
import com.shreekrishna.organics.category.dto.CategoryResponse;
import com.shreekrishna.organics.category.entity.Category;
import com.shreekrishna.organics.category.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // CREATE CATEGORY
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {

        if (categoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw new IllegalArgumentException("Category name already exists");
        }

        if (categoryRepository.existsBySlug(request.getSlug())) {
            throw new IllegalArgumentException("Category slug already exists");
        }

        Category category = new Category();

        category.setName(request.getName());
        category.setSlug(request.getSlug());
        category.setDescription(request.getDescription());

        category.setActive(
                request.getActive() != null ? request.getActive() : true
        );

        category.setDisplayOrder(
                request.getDisplayOrder() != null
                        ? request.getDisplayOrder()
                        : 0
        );

        Category savedCategory = categoryRepository.save(category);

        return toResponse(savedCategory);
    }

    // GET ALL ACTIVE CATEGORIES
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {

        return categoryRepository
                .findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // GET CATEGORY BY ID
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Category not found"));

        return toResponse(category);
    }

    // UPDATE CATEGORY
    @Transactional
    public CategoryResponse updateCategory(
            Long id,
            CategoryRequest request
    ) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Category not found"));

        category.setName(request.getName());
        category.setSlug(request.getSlug());
        category.setDescription(request.getDescription());

        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }

        if (request.getDisplayOrder() != null) {
            category.setDisplayOrder(request.getDisplayOrder());
        }

        Category updatedCategory = categoryRepository.save(category);

        return toResponse(updatedCategory);
    }

    // DEACTIVATE CATEGORY
    @Transactional
    public void deleteCategory(Long id) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Category not found"));

        category.setActive(false);
        categoryRepository.save(category);
    }

    // ENTITY -> RESPONSE DTO
    private CategoryResponse toResponse(Category category) {

        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getActive(),
                category.getDisplayOrder(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}