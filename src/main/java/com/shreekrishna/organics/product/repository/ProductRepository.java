package com.shreekrishna.organics.product.repository;

import com.shreekrishna.organics.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsBySlug(String slug);

    Optional<Product> findBySlug(String slug);

    List<Product> findByActiveTrueOrderByDisplayOrderAsc();

    List<Product> findByCategoryIdAndActiveTrueOrderByDisplayOrderAsc(
            Long categoryId
    );

    List<Product> findByFeaturedTrueAndActiveTrueOrderByDisplayOrderAsc();
}