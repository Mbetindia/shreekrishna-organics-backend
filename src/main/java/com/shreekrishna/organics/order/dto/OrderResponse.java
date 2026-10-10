package com.shreekrishna.organics.order.dto;

import com.shreekrishna.organics.order.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(Long id, String orderNumber, OrderStatus status,
                            PaymentMethod paymentMethod, PaymentStatus paymentStatus,
                            StockState stockState, BigDecimal subtotal,
                            BigDecimal deliveryCharge, BigDecimal discountAmount,
                            BigDecimal taxAmount, BigDecimal totalAmount,
                            String shippingFullName, String shippingMobile,
                            String shippingAddressLine1, String shippingAddressLine2,
                            String shippingLandmark, String shippingCity,
                            String shippingState, String shippingPostalCode,
                            String shippingCountry, LocalDateTime createdAt,
                            List<OrderItemResponse> items) {}
