package com.shreekrishna.organics.product.entity;

import com.shreekrishna.organics.category.entity.Category;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products", uniqueConstraints = {
        @UniqueConstraint(name = "uk_products_slug", columnNames = "slug")
})
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 220)
    private String slug;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal mrp;

    @Column(name = "hsn_code", length = 20)
    private String hsnCode;

    @Column(name = "gst_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRate = BigDecimal.ZERO;

    @Column(name = "price_includes_gst", nullable = false)
    private Boolean priceIncludesGst = true;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false)
    private Boolean featured = false;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "purchase_count", nullable = false)
    private Integer purchaseCount = 0;

    @Column(name = "show_purchase_count", nullable = false)
    private Boolean showPurchaseCount = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
