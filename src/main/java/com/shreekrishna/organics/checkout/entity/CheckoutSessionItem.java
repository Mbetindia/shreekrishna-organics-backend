
package com.shreekrishna.organics.checkout.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "checkout_session_items")
@Getter
@Setter
@NoArgsConstructor
public class CheckoutSessionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private CheckoutSession session;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(name = "size_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal sizeValue;

    @Column(name = "size_unit", nullable = false, length = 20)
    private String sizeUnit;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "unit_mrp", precision = 12, scale = 2)
    private BigDecimal unitMrp;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    // GST rate captured when checkout draft is created.
    @Column(name = "gst_rate", precision = 5, scale = 2)
    private BigDecimal gstRate;

    // Whether the saved product price includes GST.
    @Column(name = "price_includes_gst")
    private Boolean priceIncludesGst;

    // GST calculated for this checkout item.
    @Column(name = "tax_amount", precision = 12, scale = 2)
    private BigDecimal taxAmount;
}
