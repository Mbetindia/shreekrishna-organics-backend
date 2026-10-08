package com.shreekrishna.organics.product.service;

import com.shreekrishna.organics.category.entity.Category;
import com.shreekrishna.organics.category.repository.CategoryRepository;
import com.shreekrishna.organics.product.dto.ProductRequest;
import com.shreekrishna.organics.product.dto.ProductResponse;
import com.shreekrishna.organics.product.entity.Product;
import com.shreekrishna.organics.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    // CREATE PRODUCT
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {

        if (productRepository.existsByNameIgnoreCase(request.getName())) {
            throw new IllegalArgumentException("Product name already exists");
        }

        if (productRepository.existsBySlug(request.getSlug())) {
            throw new IllegalArgumentException("Product slug already exists");
        }

        Category category = categoryRepository
                .findById(request.getCategoryId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Category not found")
                );

        Product product = new Product();

        product.setCategory(category);
        product.setName(request.getName());
        product.setSlug(request.getSlug());
        product.setShortDescription(request.getShortDescription());
        product.setDescription(request.getDescription());
        product.setBasePrice(request.getBasePrice());
        product.setMrp(request.getMrp());

        product.setActive(
                request.getActive() != null
                        ? request.getActive()
                        : true
        );

        product.setFeatured(
                request.getFeatured() != null
                        ? request.getFeatured()
                        : false
        );

        product.setDisplayOrder(
                request.getDisplayOrder() != null
                        ? request.getDisplayOrder()
                        : 0
        );

        Product savedProduct = productRepository.save(product);

        return toResponse(savedProduct);
    }

    // GET ALL ACTIVE PRODUCTS
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {

        return productRepository
                .findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // GET PRODUCT BY ID
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Product not found")
                );

        return toResponse(product);
    }

    // GET PRODUCTS BY CATEGORY
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByCategory(Long categoryId) {

        return productRepository
                .findByCategoryIdAndActiveTrueOrderByDisplayOrderAsc(categoryId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // GET FEATURED PRODUCTS
    @Transactional(readOnly = true)
    public List<ProductResponse> getFeaturedProducts() {

        return productRepository
                .findByFeaturedTrueAndActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // UPDATE PRODUCT
    @Transactional
    public ProductResponse updateProduct(
            Long id,
            ProductRequest request
    ) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Product not found")
                );

        Category category = categoryRepository
                .findById(request.getCategoryId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Category not found")
                );

        product.setCategory(category);
        product.setName(request.getName());
        product.setSlug(request.getSlug());
        product.setShortDescription(request.getShortDescription());
        product.setDescription(request.getDescription());
        product.setBasePrice(request.getBasePrice());
        product.setMrp(request.getMrp());

        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }

        if (request.getFeatured() != null) {
            product.setFeatured(request.getFeatured());
        }

        if (request.getDisplayOrder() != null) {
            product.setDisplayOrder(request.getDisplayOrder());
        }

        Product updatedProduct = productRepository.save(product);

        return toResponse(updatedProduct);
    }

    // SOFT DELETE PRODUCT
    @Transactional
    public void deleteProduct(Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Product not found")
                );

        product.setActive(false);

        productRepository.save(product);
    }

    // ENTITY -> RESPONSE DTO
    private ProductResponse toResponse(Product product) {

        return new ProductResponse(
                product.getId(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getName(),
                product.getSlug(),
                product.getShortDescription(),
                product.getDescription(),
                product.getBasePrice(),
                product.getMrp(),
                product.getActive(),
                product.getFeatured(),
                product.getDisplayOrder(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}