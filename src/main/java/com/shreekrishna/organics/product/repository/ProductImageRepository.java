package com.shreekrishna.organics.product.repository;

import com.shreekrishna.organics.product.entity.ProductImage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductImageRepository
        extends JpaRepository<ProductImage, Long> {

    // ==========================================
    // EXISTING METHODS - KEEP UNCHANGED
    // ==========================================

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

    // Count all images belonging to a product
    long countByProductId(Long productId);

    // Get all images belonging to a product
    List<ProductImage> findByProductId(Long productId);


    // ==========================================
    // NORMAL PRODUCT IMAGES
    // ==========================================

    /*
     * Get only normal product images.
     *
     * Includes:
     * - Primary image
     * - Gallery images
     *
     * Excludes:
     * - Variant-specific images
     */

    List<ProductImage>
    findByProductIdAndVariantIsNullOrderByDisplayOrderAscIdAsc(
            Long productId
    );

    // Count only normal product images
    long countByProductIdAndVariantIsNull(
            Long productId
    );

    // Find normal primary image
    Optional<ProductImage>
    findByProductIdAndVariantIsNullAndPrimaryTrue(
            Long productId
    );


    // ==========================================
    // VARIANT-SPECIFIC IMAGES
    // ==========================================

    /*
     * Find image belonging to a specific
     * product and variant.
     *
     * IMPORTANT:
     * ProductImage has "variant" relationship.
     * Therefore JPQL must use image.variant.id.
     */

    @Query("""
            SELECT image
            FROM ProductImage image
            WHERE image.product.id = :productId
              AND image.variant.id = :variantId
            """)
    Optional<ProductImage> findByProductIdAndVariantId(
            @Param("productId") Long productId,
            @Param("variantId") Long variantId
    );


    /*
     * Get all variant-specific images
     * belonging to a product.
     */

    List<ProductImage>
    findByProductIdAndVariantIsNotNullOrderByDisplayOrderAscIdAsc(
            Long productId
    );


    /*
     * Check whether a variant already has an image.
     */

    @Query("""
            SELECT CASE WHEN COUNT(image) > 0
                        THEN true
                        ELSE false
                   END
            FROM ProductImage image
            WHERE image.variant.id = :variantId
            """)
    boolean existsByVariantId(
            @Param("variantId") Long variantId
    );


    /*
     * Find image directly using variant ID.
     *
     * Useful for replacing an existing variant image.
     */

    @Query("""
            SELECT image
            FROM ProductImage image
            WHERE image.variant.id = :variantId
            """)
    Optional<ProductImage> findByVariantId(
            @Param("variantId") Long variantId
    );


    /*
     * Count only variant-specific images.
     */

    long countByProductIdAndVariantIsNotNull(
            Long productId
    );
}