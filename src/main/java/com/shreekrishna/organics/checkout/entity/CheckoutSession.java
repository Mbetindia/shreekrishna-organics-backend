package com.shreekrishna.organics.checkout.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "checkout_sessions")
@Getter @Setter @NoArgsConstructor
public class CheckoutSession {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "address_id", nullable = false)
    private Long addressId;


    // Immutable delivery details captured when this checkout draft is created.
    @Column(name = "shipping_full_name", length = 150)
    private String shippingFullName;
    @Column(name = "shipping_mobile", length = 20)
    private String shippingMobile;
    @Column(name = "shipping_address_line1", length = 255)
    private String shippingAddressLine1;
    @Column(name = "shipping_address_line2", length = 255)
    private String shippingAddressLine2;
    @Column(name = "shipping_landmark", length = 255)
    private String shippingLandmark;
    @Column(name = "shipping_city", length = 100)
    private String shippingCity;
    @Column(name = "shipping_state", length = 100)
    private String shippingState;
    @Column(name = "shipping_postal_code", length = 10)
    private String shippingPostalCode;
    @Column(name = "shipping_country", length = 100)
    private String shippingCountry;

    @Column(nullable = false, length = 30)
    private String status = "CREATED";

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "razorpay_order_id", length = 100, unique = true)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 100, unique = true)
    private String razorpayPaymentId;

    @Column(name = "finalized_order_id", unique = true)
    private Long finalizedOrderId;

    @Column(name = "stock_reserved", nullable = false)
    private Boolean stockReserved = false;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
