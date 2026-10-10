package com.shreekrishna.organics.order.service;

import com.shreekrishna.organics.address.entity.Address;
import com.shreekrishna.organics.address.repository.AddressRepository;
import com.shreekrishna.organics.cart.entity.Cart;
import com.shreekrishna.organics.cart.entity.CartItem;
import com.shreekrishna.organics.cart.repository.CartItemRepository;
import com.shreekrishna.organics.cart.repository.CartRepository;
import com.shreekrishna.organics.inventory.repository.InventoryRepository;
import com.shreekrishna.organics.inventory.service.InventoryService;
import com.shreekrishna.organics.order.dto.*;
import com.shreekrishna.organics.order.entity.*;
import com.shreekrishna.organics.order.repository.*;
import com.shreekrishna.organics.product.entity.Product;
import com.shreekrishna.organics.product.entity.ProductVariant;
import com.shreekrishna.organics.product.repository.ProductVariantRepository;
import com.shreekrishna.organics.user.entity.User;
import com.shreekrishna.organics.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional
public class CustomerOrderService {
    private final UserRepository users;
    private final AddressRepository addresses;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final ProductVariantRepository variants;
    private final InventoryRepository inventories;
    private final InventoryService inventoryService;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final EntityManager em;

    public CustomerOrderService(UserRepository users, AddressRepository addresses,
                                CartRepository carts, CartItemRepository cartItems,
                                ProductVariantRepository variants, InventoryRepository inventories,
                                InventoryService inventoryService, OrderRepository orders,
                                OrderItemRepository orderItems, EntityManager em) {
        this.users = users;
        this.addresses = addresses;
        this.carts = carts;
        this.cartItems = cartItems;
        this.variants = variants;
        this.inventories = inventories;
        this.inventoryService = inventoryService;
        this.orders = orders;
        this.orderItems = orderItems;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public CheckoutSummaryResponse summary(String email) {
        User user = currentUser(email);
        Cart cart = carts.findByUserId(user.getId()).orElse(null);
        if (cart == null) return new CheckoutSummaryResponse(List.of(), zero(), zero(), zero(), zero(), zero());
        List<CheckoutSummaryResponse.CheckoutItem> items = new ArrayList<>();
        BigDecimal subtotal = zero();
        for (CartItem ci : cartItems.findByCartIdOrderByIdAsc(cart.getId())) {
            ProductVariant v = variants.findById(ci.getVariantId())
                    .orElseThrow(() -> conflict("Variant no longer exists"));
            Product p = v.getProduct();
            BigDecimal line = v.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()));
            int available = inventories.findByVariant_Id(v.getId())
                    .map(i -> i.getAvailableQuantity()).orElse(0);
            boolean ok = ci.getQuantity() > 0 && Boolean.TRUE.equals(v.getActive())
                    && Boolean.TRUE.equals(p.getActive()) && available >= ci.getQuantity();
            items.add(new CheckoutSummaryResponse.CheckoutItem(v.getId(), p.getName(),
                    v.getSku(), ci.getQuantity(), v.getPrice(), line, ok));
            subtotal = subtotal.add(line);
        }
        return new CheckoutSummaryResponse(items, subtotal, zero(), zero(), zero(), subtotal);
    }

    public OrderResponse place(String email, PlaceOrderRequest request) {
        User user = currentUser(email);
        // Match CartService's user-row lock to serialize checkout and cart edits.
        em.lock(user, LockModeType.PESSIMISTIC_WRITE);
        Address address = addresses.findByIdAndUserId(request.addressId(), user.getId())
                .orElseThrow(() -> notFound("Shipping address not found"));
        Cart cart = carts.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> conflict("Cart is empty"));
        List<CartItem> selected = cartItems.findByCartIdOrderByIdAsc(cart.getId());
        if (selected.isEmpty()) throw conflict("Cart is empty");

        // Reserve variants in a consistent order to reduce lock deadlocks.
        List<CartItem> sorted = new ArrayList<>(selected);
        sorted.sort(Comparator.comparing(CartItem::getVariantId));
        Map<Long, ProductVariant> selectedVariants = new HashMap<>();
        for (CartItem ci : sorted) {
            if (ci.getQuantity() == null || ci.getQuantity() <= 0) throw conflict("Invalid cart quantity");
            ProductVariant v = variants.findById(ci.getVariantId())
                    .orElseThrow(() -> conflict("Product variant no longer exists"));
            if (!Boolean.TRUE.equals(v.getActive()) || !Boolean.TRUE.equals(v.getProduct().getActive()))
                throw conflict("Product is no longer available: " + v.getSku());
            inventoryService.reserveStock(v.getId(), ci.getQuantity());
            selectedVariants.put(v.getId(), v);
        }

        Order order = new Order();
        order.setOrderNumber("SKO-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        order.setUserId(user.getId());
        order.setAddressId(address.getId());
        order.setStatus(OrderStatus.PLACED);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setStockState(StockState.RESERVED);
        order.setCurrency("INR");
        order.setShippingFullName(address.getFullName());
        order.setShippingMobile(address.getMobile());
        order.setShippingAddressLine1(address.getAddressLine1());
        order.setShippingAddressLine2(address.getAddressLine2());
        order.setShippingLandmark(address.getLandmark());
        order.setShippingCity(address.getCity());
        order.setShippingState(address.getState());
        order.setShippingPostalCode(address.getPostalCode());
        order.setShippingCountry(address.getCountry());
        order.setDeliveryCharge(zero());
        order.setDiscountAmount(zero());
        order.setTaxAmount(zero());

        BigDecimal subtotal = zero();
        List<OrderItem> snapshots = new ArrayList<>();
        for (CartItem ci : selected) {
            ProductVariant v = selectedVariants.get(ci.getVariantId());
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setVariantId(v.getId());
            item.setProductId(v.getProduct().getId());
            item.setProductName(v.getProduct().getName());
            item.setSku(v.getSku());
            item.setSizeValue(v.getSizeValue());
            item.setSizeUnit(v.getSizeUnit());
            item.setQuantity(ci.getQuantity());
            item.setUnitPrice(v.getPrice());
            item.setUnitMrp(v.getMrp());
            item.setLineTotal(v.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())));
            subtotal = subtotal.add(item.getLineTotal());
            snapshots.add(item);
        }
        order.setSubtotal(subtotal);
        order.setTotalAmount(subtotal);
        orders.saveAndFlush(order);
        orderItems.saveAllAndFlush(snapshots);
        cartItems.deleteByCartId(cart.getId());
        cartItems.flush();
        return response(order, snapshots);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(String email) {
        Long userId = currentUser(email).getId();
        return orders.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(o -> response(o, orderItems.findByOrder_IdOrderByIdAsc(o.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(String email, Long id) {
        Long userId = currentUser(email).getId();
        Order order = orders.findByIdAndUserId(id, userId)
                .orElseThrow(() -> notFound("Order not found"));
        return response(order, orderItems.findByOrder_IdOrderByIdAsc(id));
    }

    public OrderResponse cancel(String email, Long id) {
        User user = currentUser(email);
        Order order = orders.findOwnedOrderForUpdate(id, user.getId())
                .orElseThrow(() -> notFound("Order not found"));
        if (order.getStatus() != OrderStatus.PLACED || order.getStockState() != StockState.RESERVED)
            throw conflict("This order cannot be cancelled");
        List<OrderItem> items = orderItems.findByOrder_IdOrderByIdAsc(id);
        items.stream().sorted(Comparator.comparing(OrderItem::getVariantId))
                .forEach(item -> inventoryService.releaseStock(item.getVariantId(), item.getQuantity()));
        order.setStockState(StockState.RELEASED);
        order.setStatus(OrderStatus.CANCELLED);
        orders.saveAndFlush(order);
        return response(order, items);
    }

    private OrderResponse response(Order o, List<OrderItem> items) {
        return new OrderResponse(o.getId(), o.getOrderNumber(), o.getStatus(), o.getPaymentMethod(),
                o.getPaymentStatus(), o.getStockState(), o.getSubtotal(), o.getDeliveryCharge(),
                o.getDiscountAmount(), o.getTaxAmount(), o.getTotalAmount(), o.getShippingFullName(),
                o.getShippingMobile(), o.getShippingAddressLine1(), o.getShippingAddressLine2(),
                o.getShippingLandmark(), o.getShippingCity(), o.getShippingState(),
                o.getShippingPostalCode(), o.getShippingCountry(), o.getCreatedAt(),
                items.stream().map(OrderItemResponse::from).toList());
    }

    private User currentUser(String email) {
        return users.findByEmail(email).orElseThrow(() -> notFound("Authenticated user not found"));
    }

    private static BigDecimal zero() { return BigDecimal.ZERO.setScale(2); }
    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
