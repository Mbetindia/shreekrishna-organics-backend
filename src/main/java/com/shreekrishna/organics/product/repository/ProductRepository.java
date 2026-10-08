
package com.shreekrishna.organics.product.repository;

import com.shreekrishna.organics.product.entity.Product;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // Lock product row during image modifications.
    // Must be called inside an active transaction.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :productId")
    Optional<Product> findByIdForUpdate(
            @Param("productId") Long productId
    );
}
