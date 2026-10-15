package com.shreekrishna.organics.admin.dashboard.dto;

import java.math.BigDecimal;

public record AdminDashboardResponse(
        long totalProducts,
        long totalOrders,
        long totalCustomers,
        BigDecimal revenue,
        long paidOrders,
        long pendingOrders,
        long codOrders,
        long razorpayOrders
) {
}