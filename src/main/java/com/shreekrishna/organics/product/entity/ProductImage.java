package com.shreekrishna.organics.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_images")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==========================================
    // PRODUCT RELATIONSHIP
    // ==========================================

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_product_images_product"
            )
    )
    private Product product;

    // ==========================================
    // PRODUCT VARIANT RELATIONSHIP - NEW
    // ==========================================

    /*
     * variant = null:
     * Normal Product Primary / Gallery Image
     *
     * variant != null:
     * Variant-specific Image
     *
     * Example:
     * 500 ML -> Image A
     * 1 L    -> Image B
     * 5 L    -> Image C
     */

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "variant_id",
            foreignKey = @ForeignKey(
                    name = "fk_product_images_variant"
            )
    )
    private ProductVariant variant;

    // ==========================================
    // CLOUDINARY IMAGE DETAILS
    // ==========================================

    @Column(
            name = "image_url",
            nullable = false,
            length = 2048
    )
    private String imageUrl;

    @Column(
            name = "public_id",
            nullable = false,
            unique = true,
            length = 255
    )
    private String publicId;

    // ==========================================
    // PRIMARY IMAGE
    // ==========================================

    /*
     * Only normal product images can be primary.
     *
     * Variant images must always have primary = false.
     */

    @Column(
            name = "is_primary",
            nullable = false
    )
    private Boolean primary = false;

    // ==========================================
    // IMAGE DISPLAY ORDER
    // ==========================================

    @Column(
            name = "display_order",
            nullable = false
    )
    private Integer displayOrder = 0;

    // ==========================================
    // CREATED DATE
    // ==========================================

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    // ==========================================
    // ENTITY LIFECYCLE
    // ==========================================

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // ==========================================
    // GETTERS AND SETTERS
    // ==========================================

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    // NEW - VARIANT GETTER / SETTER

    public ProductVariant getVariant() {
        return variant;
    }

    public void setVariant(ProductVariant variant) {
        this.variant = variant;
    }

    // NEW - VARIANT ID HELPER

    public Long getVariantId() {
        return variant != null
                ? variant.getId()
                : null;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public Boolean getPrimary() {
        return primary;
    }

    public void setPrimary(Boolean primary) {
        this.primary = primary;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}