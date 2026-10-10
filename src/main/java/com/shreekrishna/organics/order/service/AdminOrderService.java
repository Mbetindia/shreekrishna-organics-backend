package com.shreekrishna.organics.order.service;

import com.shreekrishna.organics.inventory.service.InventoryService;
import com.shreekrishna.organics.order.dto.*;
import com.shreekrishna.organics.order.entity.*;
import com.shreekrishna.organics.order.repository.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
@Transactional
public class AdminOrderService {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final InventoryService inventory;

    public AdminOrderService(OrderRepository orders, OrderItemRepository items, InventoryService inventory) {
        this.orders = orders;
        this.items = items;
        this.inventory = inventory;
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw badRequest("Invalid pagination parameters");
        return orders.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return toResponse(orders.findById(id).orElseThrow(() -> notFound("Order not found")));
    }

    public OrderResponse changeStatus(Long id, OrderStatus target) {
        if (target == null) throw badRequest("Status is required");
        Order order = lock(id);
        OrderStatus current = order.getStatus();
        if (current == target) return toResponse(order);
        OrderStatus expected = switch (target) {
            case CONFIRMED -> OrderStatus.PLACED;
            case PACKED -> OrderStatus.CONFIRMED;
            case SHIPPED -> OrderStatus.PACKED;
            case DELIVERED -> OrderStatus.SHIPPED;
            default -> null;
        };
        if (expected == null || current != expected) throw conflict("Invalid order status transition: " + current + " -> " + target);

        // Reservation becomes a real stock deduction on dispatch, exactly once.
        if (target == OrderStatus.SHIPPED) {
            if (order.getStockState() != StockState.RESERVED) throw conflict("Stock is not reserved");
            List<OrderItem> lines = orderedItems(id);
            for (OrderItem item : lines) {
                if (item.getVariantId() == null) throw conflict("Cannot dispatch an item without a variant");
                inventory.confirmStock(item.getVariantId(), item.getQuantity());
            }
            order.setStockState(StockState.CONFIRMED);
        }
        order.setStatus(target);
        orders.saveAndFlush(order);
        return toResponse(order);
    }

    public OrderResponse cancel(Long id) {
        Order order = lock(id);
        if (order.getStatus() != OrderStatus.PLACED && order.getStatus() != OrderStatus.CONFIRMED
                && order.getStatus() != OrderStatus.PACKED) {
            throw conflict("Order can no longer be cancelled with automatic stock release");
        }
        if (order.getStockState() != StockState.RESERVED) throw conflict("Stock is not reserved");
        for (OrderItem item : orderedItems(id)) {
            if (item.getVariantId() == null) throw conflict("Cannot release stock for an item without a variant");
            inventory.releaseStock(item.getVariantId(), item.getQuantity());
        }
        order.setStockState(StockState.RELEASED);
        order.setStatus(OrderStatus.CANCELLED);
        orders.saveAndFlush(order);
        return toResponse(order);
    }

    public OrderResponse recordCodPayment(Long id) {
        Order order = lock(id);
        if (order.getPaymentMethod() != PaymentMethod.COD) throw conflict("Not a COD order");
        if (order.getStatus() != OrderStatus.DELIVERED) throw conflict("Mark order delivered before recording COD collection");
        if (order.getPaymentStatus() == PaymentStatus.PAID) return toResponse(order);
        if (order.getPaymentStatus() != PaymentStatus.PENDING) throw conflict("Payment cannot be marked paid");
        order.setPaymentStatus(PaymentStatus.PAID);
        orders.saveAndFlush(order);
        return toResponse(order);
    }

    private Order lock(Long id) {
        return orders.findByIdForUpdate(id).orElseThrow(() -> notFound("Order not found"));
    }

    private List<OrderItem> orderedItems(Long id) {
        List<OrderItem> result = new ArrayList<>(items.findByOrder_IdOrderByIdAsc(id));
        result.sort(Comparator.comparing(OrderItem::getVariantId, Comparator.nullsLast(Long::compareTo)));
        return result;
    }

    private OrderResponse toResponse(Order o) {
        return new OrderResponse(o.getId(), o.getOrderNumber(), o.getStatus(), o.getPaymentMethod(),
                o.getPaymentStatus(), o.getStockState(), o.getSubtotal(), o.getDeliveryCharge(),
                o.getDiscountAmount(), o.getTaxAmount(), o.getTotalAmount(), o.getShippingFullName(),
                o.getShippingMobile(), o.getShippingAddressLine1(), o.getShippingAddressLine2(),
                o.getShippingLandmark(), o.getShippingCity(), o.getShippingState(),
                o.getShippingPostalCode(), o.getShippingCountry(), o.getCreatedAt(),
                items.findByOrder_IdOrderByIdAsc(o.getId()).stream().map(OrderItemResponse::from).toList());
    }

    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
