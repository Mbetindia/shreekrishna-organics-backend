
        package com.shreekrishna.organics.checkout.service;

import com.razorpay.RazorpayException;
import com.shreekrishna.organics.cart.entity.Cart;
import com.shreekrishna.organics.cart.entity.CartItem;
import com.shreekrishna.organics.cart.repository.CartRepository;
import com.shreekrishna.organics.cart.repository.CartItemRepository;
import com.shreekrishna.organics.checkout.dto.CheckoutVerifyRequest;
import com.shreekrishna.organics.checkout.dto.CheckoutVerifyResponse;
import com.shreekrishna.organics.checkout.entity.CheckoutSession;
import com.shreekrishna.organics.checkout.entity.CheckoutSessionItem;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionRepository;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionItemRepository;
import com.shreekrishna.organics.inventory.service.InventoryService;
import com.shreekrishna.organics.order.entity.*;
import com.shreekrishna.organics.order.repository.OrderRepository;
import com.shreekrishna.organics.order.repository.OrderItemRepository;
import com.shreekrishna.organics.payment.entity.Payment;
import com.shreekrishna.organics.payment.repository.PaymentRepository;
import com.shreekrishna.organics.payment.service.RazorpayService;
import com.shreekrishna.organics.payment.service.RazorpayVerificationService;
import com.shreekrishna.organics.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@Service
public class CheckoutPaymentFinalizationService {
    private final CheckoutSessionRepository sessions;
    private final CheckoutSessionItemRepository sessionItems;
    private final UserRepository users;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final PaymentRepository payments;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final InventoryService stock;
    private final RazorpayService provider;
    private final RazorpayVerificationService signatures;

    public CheckoutPaymentFinalizationService(
            CheckoutSessionRepository sessions, CheckoutSessionItemRepository sessionItems,
            UserRepository users, OrderRepository orders,
            OrderItemRepository orderItems, PaymentRepository payments, CartRepository carts,
            CartItemRepository cartItems, InventoryService stock, RazorpayService provider,
            RazorpayVerificationService signatures) {
        this.sessions = sessions;
        this.sessionItems = sessionItems;
        this.users = users;
        this.orders = orders;
        this.orderItems = orderItems;
        this.payments = payments;
        this.carts = carts;
        this.cartItems = cartItems;
        this.stock = stock;
        this.provider = provider;
        this.signatures = signatures;
    }

    @Transactional
    public CheckoutVerifyResponse verify(String email, String sessionId, CheckoutVerifyRequest request)
            throws RazorpayException {
        if (request == null || blank(request.razorpayOrderId())
                || blank(request.razorpayPaymentId()) || blank(request.razorpaySignature())) {
            throw problem(HttpStatus.BAD_REQUEST, "Missing Razorpay verification fields");
        }
        Long userId = findCustomerId(email);
        CheckoutSession session = sessions.findOwnedForUpdate(sessionId, userId)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Checkout session not found"));
        if (!request.razorpayOrderId().equals(session.getRazorpayOrderId())) {
            throw problem(HttpStatus.CONFLICT, "Razorpay order does not match checkout session");
        }
        if (!signatures.verifyPayment(request.razorpayOrderId(), request.razorpayPaymentId(),
                request.razorpaySignature())) {
            throw problem(HttpStatus.BAD_REQUEST, "Invalid Razorpay payment signature");
        }

        com.razorpay.Payment remote = provider.fetchPayment(request.razorpayPaymentId());
        String state = remote.get("status");
        String remoteOrder = remote.get("order_id");
        String currency = remote.get("currency");
        Number amount = remote.get("amount");
        long expected = session.getTotalAmount().movePointRight(2).longValueExact();
        if (!"captured".equals(state) || !request.razorpayOrderId().equals(remoteOrder)
                || !"INR".equals(currency) || !"INR".equals(session.getCurrency())
                || amount == null || amount.longValue() != expected) {
            throw problem(HttpStatus.CONFLICT, "Payment is not captured or details do not match");
        }
        return finalizeCapturedLocked(session, request.razorpayPaymentId());
    }

    @Transactional
    public CheckoutVerifyResponse finalizeCapturedWebhook(String razorpayOrderId, String razorpayPaymentId,
                                                          long amountPaise, String currency) {
        CheckoutSession found = sessions.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Checkout session not found"));
        CheckoutSession session = sessions.findByIdForUpdate(found.getId())
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Checkout session not found"));
        if (!razorpayOrderId.equals(session.getRazorpayOrderId()) || !"INR".equals(currency)
                || !currency.equals(session.getCurrency())
                || session.getTotalAmount().movePointRight(2).longValueExact() != amountPaise) {
            throw problem(HttpStatus.CONFLICT, "Captured webhook details do not match session");
        }
        return finalizeCapturedLocked(session, razorpayPaymentId);
    }

    private CheckoutVerifyResponse finalizeCapturedLocked(CheckoutSession s, String paymentId) {
        if ("FINALIZED".equals(s.getStatus())) {
            if (!paymentId.equals(s.getRazorpayPaymentId()) || s.getFinalizedOrderId() == null) {
                throw problem(HttpStatus.CONFLICT, "Session already finalized with another payment");
            }
            return new CheckoutVerifyResponse(s.getId(), s.getStatus(), s.getFinalizedOrderId());
        }

        // A customer may have deliberately switched this checkout to COD after provider
        // reconciliation found no live payment. If a late capture arrives, attach it to
        // the SAME order and convert that order to online-paid. Never create a duplicate.
        if ("COD_FINALIZED".equals(s.getStatus()) && s.getFinalizedOrderId() != null) {
            return convertCodOrderAfterLateCapture(s, paymentId);
        }

        if (!"PAYMENT_PENDING".equals(s.getStatus()) || !Boolean.TRUE.equals(s.getStockReserved())
                || blank(s.getRazorpayOrderId()) || blank(paymentId)) {
            throw problem(HttpStatus.CONFLICT, "Session not eligible; payment needs reconciliation");
        }

        List<CheckoutSessionItem> snapshots = validateSnapshots(s);

        Order o = new Order();
        o.setOrderNumber("SKO-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        o.setUserId(s.getUserId());
        o.setAddressId(s.getAddressId());
        o.setStatus(OrderStatus.PLACED);
        o.setPaymentMethod(PaymentMethod.RAZORPAY);
        o.setPaymentStatus(PaymentStatus.PAID);
        o.setStockState(StockState.RESERVED);
        o.setCurrency(s.getCurrency());
        o.setSubtotal(s.getSubtotal());
        o.setTotalAmount(s.getTotalAmount());
        o.setCodCharge(BigDecimal.ZERO.setScale(2));
        o.setDeliveryCharge(s.getDeliveryCharge());
        o.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        o.setTaxAmount(totalSnapshotTax(snapshots));
        copyShipping(s, o);
        orders.saveAndFlush(o);

        List<OrderItem> copies = copyOrderItems(o, snapshots);
        orderItems.saveAllAndFlush(copies);

        Payment p = new Payment();
        p.setOrderId(o.getId());
        p.setProvider("RAZORPAY");
        p.setStatus(com.shreekrishna.organics.payment.entity.PaymentStatus.PAID);
        p.setRazorpayOrderId(s.getRazorpayOrderId());
        p.setRazorpayPaymentId(paymentId);
        p.setAmount(s.getTotalAmount());
        p.setCurrency(s.getCurrency());
        payments.saveAndFlush(p);

        s.setRazorpayPaymentId(paymentId);
        s.setFinalizedOrderId(o.getId());
        s.setStatus("FINALIZED");
        sessions.saveAndFlush(s);

        if (!"BUY_NOW".equals(s.getCheckoutSource())) {
            removePurchasedItemsFromCart(s.getUserId(), snapshots);
        }
        return new CheckoutVerifyResponse(s.getId(), s.getStatus(), o.getId());
    }

    private CheckoutVerifyResponse convertCodOrderAfterLateCapture(CheckoutSession s, String paymentId) {
        if (blank(paymentId) || !Boolean.TRUE.equals(s.getStockReserved())) {
            throw problem(HttpStatus.CONFLICT, "Late captured payment needs manual reconciliation");
        }

        Order order = orders.findByIdForUpdate(s.getFinalizedOrderId())
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Finalized COD order not found"));

        if (!order.getUserId().equals(s.getUserId())) {
            throw problem(HttpStatus.CONFLICT, "Checkout and COD order ownership mismatch");
        }

        Optional<Payment> existingByPaymentId = payments.findByRazorpayPaymentId(paymentId);
        if (existingByPaymentId.isPresent()) {
            Payment existing = existingByPaymentId.get();
            if (!existing.getOrderId().equals(order.getId())
                    || !s.getRazorpayOrderId().equals(existing.getRazorpayOrderId())) {
                throw problem(HttpStatus.CONFLICT, "Captured payment already belongs to another order");
            }
            s.setRazorpayPaymentId(paymentId);
            s.setStatus("FINALIZED");
            sessions.saveAndFlush(s);
            return new CheckoutVerifyResponse(s.getId(), s.getStatus(), order.getId());
        }

        if (order.getStatus() != OrderStatus.PLACED
                || order.getStockState() != StockState.RESERVED
                || order.getPaymentStatus() != PaymentStatus.PENDING
                || order.getPaymentMethod() != PaymentMethod.COD) {
            throw problem(HttpStatus.CONFLICT,
                    "A late online payment was captured after the COD order changed state; manual refund/reconciliation is required");
        }

        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setProvider("RAZORPAY");
        payment.setStatus(com.shreekrishna.organics.payment.entity.PaymentStatus.PAID);
        payment.setRazorpayOrderId(s.getRazorpayOrderId());
        payment.setRazorpayPaymentId(paymentId);
        payment.setAmount(s.getTotalAmount());
        payment.setCurrency(s.getCurrency());
        payments.saveAndFlush(payment);

        order.setPaymentMethod(PaymentMethod.RAZORPAY);
        order.setPaymentStatus(PaymentStatus.PAID);
        List<CheckoutSessionItem> snapshots = validateSnapshots(s);
        order.setTaxAmount(totalSnapshotTax(snapshots));
        order.setCodCharge(BigDecimal.ZERO.setScale(2));
        order.setSubtotal(s.getSubtotal());
        order.setDeliveryCharge(s.getDeliveryCharge());
        order.setTotalAmount(s.getTotalAmount());
        orders.saveAndFlush(order);

        s.setRazorpayPaymentId(paymentId);
        s.setStatus("FINALIZED");
        sessions.saveAndFlush(s);

        return new CheckoutVerifyResponse(s.getId(), s.getStatus(), order.getId());
    }

    private List<CheckoutSessionItem> validateSnapshots(CheckoutSession s) {
        List<CheckoutSessionItem> snapshots = sessionItems.findBySession_IdOrderByIdAsc(s.getId());
        if (snapshots.isEmpty()) throw problem(HttpStatus.CONFLICT, "Checkout session is empty");

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal additionalTax = BigDecimal.ZERO;
        boolean legacy = snapshots.stream().allMatch(i -> i.getTaxAmount() == null
                && i.getGstRate() == null && i.getPriceIncludesGst() == null);

        for (CheckoutSessionItem item : snapshots) {
            if (item.getLineTotal() == null || item.getLineTotal().signum() < 0) {
                throw problem(HttpStatus.CONFLICT, "Invalid checkout item amount");
            }
            subtotal = subtotal.add(item.getLineTotal());
            if (legacy) continue;
            if (item.getTaxAmount() == null || item.getGstRate() == null
                    || item.getPriceIncludesGst() == null || item.getTaxAmount().signum() < 0
                    || item.getGstRate().signum() < 0
                    || item.getGstRate().compareTo(new BigDecimal("100")) > 0) {
                throw problem(HttpStatus.CONFLICT, "Incomplete GST checkout snapshot; payment needs reconciliation");
            }
            if (!item.getPriceIncludesGst()) additionalTax = additionalTax.add(item.getTaxAmount());
        }
        if (s.getSubtotal() == null || s.getTotalAmount() == null
                || s.getDeliveryCharge() == null || s.getDeliveryCharge().signum() < 0
                || s.getSubtotal().signum() <= 0
                || subtotal.compareTo(s.getSubtotal()) != 0
                || s.getTotalAmount().compareTo(s.getSubtotal().add(s.getDeliveryCharge()).add(additionalTax)) != 0) {
            throw problem(HttpStatus.CONFLICT, "Checkout snapshot total mismatch");
        }
        if (blank(s.getShippingFullName()) || blank(s.getShippingMobile())
                || blank(s.getShippingAddressLine1()) || blank(s.getShippingCity())
                || blank(s.getShippingState()) || blank(s.getShippingPostalCode())
                || blank(s.getShippingCountry())) {
            throw problem(HttpStatus.CONFLICT,
                    "Checkout shipping snapshot is missing; manual reconciliation required");
        }
        return snapshots;
    }

    private BigDecimal totalSnapshotTax(List<CheckoutSessionItem> snapshots) {
        return snapshots.stream().map(i -> i.getTaxAmount() == null ? BigDecimal.ZERO : i.getTaxAmount())
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private List<OrderItem> copyOrderItems(Order order, List<CheckoutSessionItem> snapshots) {
        List<OrderItem> copies = new ArrayList<>();
        for (CheckoutSessionItem item : snapshots) {
            OrderItem copy = new OrderItem();
            copy.setOrder(order);
            copy.setVariantId(item.getVariantId());
            copy.setProductId(item.getProductId());
            copy.setProductName(item.getProductName());
            copy.setSku(item.getSku());
            copy.setSizeValue(item.getSizeValue());
            copy.setSizeUnit(item.getSizeUnit());
            copy.setQuantity(item.getQuantity());
            copy.setUnitPrice(item.getUnitPrice());
            copy.setUnitMrp(item.getUnitMrp());
            copy.setLineTotal(item.getLineTotal());
            copies.add(copy);
        }
        return copies;
    }

    private void copyShipping(CheckoutSession s, Order o) {
        o.setShippingFullName(s.getShippingFullName());
        o.setShippingMobile(s.getShippingMobile());
        o.setShippingAddressLine1(s.getShippingAddressLine1());
        o.setShippingAddressLine2(s.getShippingAddressLine2());
        o.setShippingLandmark(s.getShippingLandmark());
        o.setShippingCity(s.getShippingCity());
        o.setShippingState(s.getShippingState());
        o.setShippingPostalCode(s.getShippingPostalCode());
        o.setShippingCountry(s.getShippingCountry());
    }

    private void removePurchasedItemsFromCart(Long userId, List<CheckoutSessionItem> snapshots) {
        Optional<Cart> cart = carts.findByUserIdForUpdate(userId);
        if (cart.isEmpty()) return;

        List<CartItem> current = cartItems.findByCartIdOrderByIdAsc(cart.get().getId());
        Map<Long, Integer> purchasedQtyByVariant = new HashMap<>();
        for (CheckoutSessionItem snapshot : snapshots) {
            purchasedQtyByVariant.merge(snapshot.getVariantId(), snapshot.getQuantity(), Integer::sum);
        }

        for (CartItem item : current) {
            Integer purchasedQty = purchasedQtyByVariant.get(item.getVariantId());
            if (purchasedQty == null || purchasedQty <= 0) continue;
            int remaining = item.getQuantity() - purchasedQty;
            if (remaining <= 0) {
                cartItems.delete(item);
            } else {
                item.setQuantity(remaining);
                cartItems.save(item);
            }
        }
        cartItems.flush();
    }

    // The caller must supply the authenticated principal (email or mobile),
    // never an identifier supplied by the client request body.
    private Long findCustomerId(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw problem(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String value = identifier.trim();
        if (value.contains("@")) {
            return users.findByEmail(value.toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Customer not found"))
                    .getId();
        }
        return users.findByMobile(value)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Customer not found"))
                .getId();
    }

    private boolean blank(String v) { return v == null || v.isBlank(); }
    private ResponseStatusException problem(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
