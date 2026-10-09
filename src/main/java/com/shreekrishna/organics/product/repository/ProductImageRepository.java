
package com.shreekrishna.organics.product.repository;

import com.shreekrishna.organics.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductImageRepository
        extends JpaRepository<ProductImage, Long> {

    // Get all images in display order
    List<ProductImage> findByProductIdOrderByDisplayOrderAscIdAsc(
            Long productId
    );

    // Find a specific image belonging to a product
    Optional<ProductImage> findByIdAndProductId(
            Long imageId,
            Long productId
    );

    // Find the primary image of a product
    Optional<ProductImage> findByProductIdAndPrimaryTrue(
            Long productId
    );

    // Count images belonging to a product
    long countByProductId(Long productId);

    // Get all images belonging to a product
    List<ProductImage> findByProductId(Long productId);
}
