package com.shreekrishna.organics.order.dto;

import com.shreekrishna.organics.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record AdminOrderStatusRequest(@NotNull OrderStatus status) {}
