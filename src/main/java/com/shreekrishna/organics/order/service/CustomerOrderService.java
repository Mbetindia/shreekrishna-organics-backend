

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
import com.shreekrishna.organics.product.service.VariantDeliveryPricingService;
import com.shreekrishna.organics.user.entity.User;
import com.shreekrishna.organics.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.shreekrishna.organics.payment.repository.PaymentRepository;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final PaymentRepository paymentRepository;
    private final CheckoutSessionRepository checkoutSessions;
    private final VariantDeliveryPricingService deliveryPricingService;

    public CustomerOrderService(UserRepository users, AddressRepository addresses,
                                CartRepository carts, CartItemRepository cartItems,
                                ProductVariantRepository variants, InventoryRepository inventories,
                                InventoryService inventoryService, OrderRepository orders,
                                OrderItemRepository orderItems, EntityManager em,
                                PaymentRepository paymentRepository,
                                CheckoutSessionRepository checkoutSessions,
                                VariantDeliveryPricingService deliveryPricingService) {
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
        this.paymentRepository = paymentRepository;
        this.checkoutSessions = checkoutSessions;
        this.deliveryPricingService = deliveryPricingService;
    }

    @Transactional(readOnly = true)
    public CheckoutSummaryResponse summary(String email) {
        User user = currentUser(email);
        Cart cart = carts.findByUserId(user.getId()).orElse(null);
        if (cart == null) return new CheckoutSummaryResponse(List.of(), zero(), zero(), zero(), zero(), zero());
        List<CheckoutSummaryResponse.CheckoutItem> items = new ArrayList<>();
        BigDecimal subtotal = zero();
        BigDecimal taxAmount = zero();
        BigDecimal additionalTax = zero();
        List<CartItem> selected = cartItems.findByCartIdOrderByIdAsc(cart.getId());
        for (CartItem ci : selected) {
            if (ci.getQuantity() == null || ci.getQuantity() <= 0) {
                throw conflict("Invalid cart quantity");
            }
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
            TaxBreakdown tax = calculateProductTax(p, line);
            taxAmount = taxAmount.add(tax.taxAmount());
            additionalTax = additionalTax.add(tax.additionalTax());
        }
        BigDecimal deliveryCharge = calculateCartDeliveryCharge(selected);
        BigDecimal total = subtotal.add(deliveryCharge).add(additionalTax);
        return new CheckoutSummaryResponse(items, subtotal, deliveryCharge, zero(), taxAmount, total);
    }

    public OrderResponse place(String email, PlaceOrderRequest request) {
        User user = currentUser(email);
        // Match CartService's user-row lock to serialize checkout and cart edits.
        em.lock(user, LockModeType.PESSIMISTIC_WRITE);
        // The legacy /orders endpoint is COD-only. Online orders must be finalized
        // by the verified checkout-session payment flow, never created before payment.
        if (request == null || request.paymentMethod() != PaymentMethod.COD) {
            throw conflict("Online payment must use the checkout-session flow; this endpoint accepts COD only");
        }
        // Do not place COD while a captured checkout-session payment might be pending.
        if (checkoutSessions.existsByUserIdAndStatusInAndIdNot(user.getId(),
                List.of("PAYMENT_INITIATING", "PAYMENT_PENDING"), "__no_matching_session__")) {
            throw conflict("An online payment is unresolved. Reconcile it before placing another order");
        }
        Address address = addresses.findByIdAndUserId(request.addressId(), user.getId())
                .orElseThrow(() -> notFound("Shipping address not found"));
        Cart cart = carts.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> conflict("Cart is empty"));
        List<CartItem> selected = cartItems.findByCartIdOrderByIdAsc(cart.getId());
        if (selected.isEmpty()) throw conflict("Cart is empty");

        // Calculate from server-side cart quantities, never from client totals.
        BigDecimal deliveryCharge = calculateCartDeliveryCharge(selected);

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
        order.setPaymentMethod(request.paymentMethod());
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
        order.setDeliveryCharge(deliveryCharge);
        order.setDiscountAmount(zero());
        // Set after calculating each product line (included and excluded GST).

        BigDecimal subtotal = zero();
        BigDecimal taxAmount = zero();
        BigDecimal additionalTax = zero();
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
            TaxBreakdown tax = calculateProductTax(v.getProduct(), item.getLineTotal());
            taxAmount = taxAmount.add(tax.taxAmount());
            additionalTax = additionalTax.add(tax.additionalTax());
            snapshots.add(item);
        }
        order.setSubtotal(subtotal);
        order.setTaxAmount(taxAmount);

        BigDecimal codCharge = BigDecimal.ZERO.setScale(2);
        BigDecimal finalAmount = subtotal.add(deliveryCharge).add(additionalTax);

        if (request.paymentMethod() == PaymentMethod.COD) {
            codCharge = subtotal
                    .multiply(new BigDecimal("0.05"))
                    .setScale(2, RoundingMode.HALF_UP);

            finalAmount = finalAmount.add(codCharge);
        }

        order.setCodCharge(codCharge);
        order.setTotalAmount(finalAmount);
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

        if (order.getStatus() != OrderStatus.PLACED
                || order.getStockState() != StockState.RESERVED) {
            throw conflict("This order cannot be cancelled");
        }

        // Paid Razorpay order cancellation protection
        if (order.getPaymentMethod() == PaymentMethod.RAZORPAY
                && order.getPaymentStatus() == PaymentStatus.PAID) {

            throw conflict(
                    "Paid Razorpay order cannot be cancelled directly"
            );
        }

        // NEW CODE - Payment initiated protection
        if (order.getPaymentMethod() == PaymentMethod.RAZORPAY
                && !paymentRepository.findByOrderId(id).isEmpty()) {

            throw conflict(
                    "Razorpay payment already initiated. Cancellation requires payment reconciliation"
            );
        }

        // Existing stock release code
        List<OrderItem> items =
                orderItems.findByOrder_IdOrderByIdAsc(id);

        items.stream()
                .sorted(Comparator.comparing(OrderItem::getVariantId))
                .forEach(item ->
                        inventoryService.releaseStock(
                                item.getVariantId(),
                                item.getQuantity()
                        )
                );

        order.setStockState(StockState.RELEASED);
        order.setStatus(OrderStatus.CANCELLED);

        orders.saveAndFlush(order);

        return response(order, items);
    }
    private OrderResponse response(Order o, List<OrderItem> items) {
        OrderTaxView.Breakdown tax = OrderTaxView.from(o);
        return new OrderResponse(o.getId(), o.getOrderNumber(), o.getStatus(), o.getPaymentMethod(),
                o.getPaymentStatus(), o.getStockState(), o.getSubtotal(), o.getDeliveryCharge(),
                o.getDiscountAmount(), o.getTaxAmount(), tax.taxableAmount(), tax.effectiveGstRate(),
                tax.cgstAmount(), tax.sgstAmount(), tax.igstAmount(), tax.gstIncludedInSubtotal(),
                o.getCodCharge(), o.getTotalAmount(), o.getShippingFullName(),
                o.getShippingMobile(), o.getShippingAddressLine1(), o.getShippingAddressLine2(),
                o.getShippingLandmark(), o.getShippingCity(), o.getShippingState(),
                o.getShippingPostalCode(), o.getShippingCountry(), o.getCreatedAt(),
                items.stream().map(OrderItemResponse::from).toList());
    }

    /**
     * Product GST only. Inclusive GST is reported but not added again;
     * exclusive GST is both reported and added to the payable amount.
     * Delivery/COD tax treatment must be reviewed separately for invoicing.
     */
    private static TaxBreakdown calculateProductTax(Product product, BigDecimal lineTotal) {
        BigDecimal rate = product.getGstRate() == null ? BigDecimal.ZERO : product.getGstRate();
        if (rate.signum() < 0 || rate.compareTo(new BigDecimal("100")) > 0) {
            throw conflict("Invalid GST rate for product: " + product.getName());
        }
        if (rate.signum() == 0) return new TaxBreakdown(zero(), zero());

        BigDecimal tax;
        if (Boolean.TRUE.equals(product.getPriceIncludesGst())) {
            tax = lineTotal.multiply(rate)
                    .divide(new BigDecimal("100").add(rate), 2, RoundingMode.HALF_UP);
            return new TaxBreakdown(tax, zero());
        }
        tax = lineTotal.multiply(rate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return new TaxBreakdown(tax, tax);
    }

    private record TaxBreakdown(BigDecimal taxAmount, BigDecimal additionalTax) {}

    /** One delivery charge per variant quantity tier, not per individual unit. */
    private BigDecimal calculateCartDeliveryCharge(List<CartItem> selected) {
        Map<Long, Integer> quantities = new TreeMap<>();
        for (CartItem ci : selected) {
            if (ci.getVariantId() == null || ci.getQuantity() == null || ci.getQuantity() <= 0) {
                throw conflict("Invalid cart item quantity or variant");
            }
            try {
                quantities.merge(ci.getVariantId(), ci.getQuantity(), Math::addExact);
            } catch (ArithmeticException ex) {
                throw conflict("Cart quantity is too large");
            }
        }

        List<VariantDeliveryPricingService.VariantQuantity> items = quantities.entrySet()
                .stream()
                .map(e -> new VariantDeliveryPricingService.VariantQuantity(e.getKey(), e.getValue()))
                .toList();
        try {
            return deliveryPricingService.calculateTotalDeliveryCharge(items);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw conflict(ex.getMessage());
        }
    }

    private User currentUser(String username) {
        if (username == null || username.isBlank()) {
            throw notFound("Authenticated user not found");
        }

        String identifier = username.trim();
        if (identifier.contains("@")) {
            return users.findByEmail(identifier.toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> notFound("Authenticated user not found"));
        }

        return users.findByMobile(identifier)
                .orElseThrow(() -> notFound("Authenticated user not found"));
    }

    private static BigDecimal zero() { return BigDecimal.ZERO.setScale(2); }
    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
