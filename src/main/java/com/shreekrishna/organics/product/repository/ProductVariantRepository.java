
package com.shreekrishna.organics.product.repository;

import com.shreekrishna.organics.product.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository
        extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findBySku(String sku);

    boolean existsBySku(String sku);

    List<ProductVariant> findByProduct_Id(Long productId);

    List<ProductVariant> findByProduct_IdAndActiveTrue(Long productId);
}

