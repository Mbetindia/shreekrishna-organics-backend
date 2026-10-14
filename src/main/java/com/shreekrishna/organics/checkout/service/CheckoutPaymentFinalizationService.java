package com.shreekrishna.organics.checkout.service;

import com.razorpay.RazorpayException;
import com.shreekrishna.organics.address.entity.Address;
import com.shreekrishna.organics.address.repository.AddressRepository;
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

/**
 * Stage 3B: verified, transactionally idempotent order finalization.
 * Not ready for live activation until session webhook routing and recovery are in place.
 */
@Service
public class CheckoutPaymentFinalizationService {
    private final CheckoutSessionRepository sessions;
    private final CheckoutSessionItemRepository sessionItems;
    private final UserRepository users;
    private final AddressRepository addresses;
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
            UserRepository users, AddressRepository addresses, OrderRepository orders,
            OrderItemRepository orderItems, PaymentRepository payments, CartRepository carts,
            CartItemRepository cartItems, InventoryService stock, RazorpayService provider,
            RazorpayVerificationService signatures) {
        this.sessions = sessions;
        this.sessionItems = sessionItems;
        this.users = users;
        this.addresses = addresses;
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
        if (request == null || blank(request.razorpayOrderId()) ||
                blank(request.razorpayPaymentId()) || blank(request.razorpaySignature())) {
            throw problem(HttpStatus.BAD_REQUEST, "Missing Razorpay verification fields");
        }
        Long userId = users.findByEmail(email)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Customer not found")).getId();
        CheckoutSession session = sessions.findOwnedForUpdate(sessionId, userId)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Checkout session not found"));
        if (!request.razorpayOrderId().equals(session.getRazorpayOrderId())) {
            throw problem(HttpStatus.CONFLICT, "Razorpay order does not match checkout session");
        }
        if (!signatures.verifyPayment(request.razorpayOrderId(), request.razorpayPaymentId(),
                request.razorpaySignature())) {
            throw problem(HttpStatus.BAD_REQUEST, "Invalid Razorpay payment signature");
        }
        // Check real Razorpay capture state, not just the browser callback.
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

    /**
     * Only call this from a webhook handler AFTER Razorpay webhook signature has
     * been validated and captured-event fields (order, payment, amount, currency)
     * have been checked independently. Handler must own the transaction.
     */
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
            if (!paymentId.equals(s.getRazorpayPaymentId()) || s.getFinalizedOrderId() == null)
                throw problem(HttpStatus.CONFLICT, "Session already finalized with another payment");
            return new CheckoutVerifyResponse(s.getId(), s.getStatus(), s.getFinalizedOrderId());
        }
        if (!"PAYMENT_PENDING".equals(s.getStatus()) || !Boolean.TRUE.equals(s.getStockReserved())
                || blank(s.getRazorpayOrderId()) || blank(paymentId)) {
            throw problem(HttpStatus.CONFLICT, "Session not eligible; payment needs reconciliation");
        }
        List<CheckoutSessionItem> snapshots = sessionItems.findBySession_IdOrderByIdAsc(s.getId());
        if (snapshots.isEmpty()) throw problem(HttpStatus.CONFLICT, "Checkout session is empty");
        BigDecimal recalculated = snapshots.stream().map(CheckoutSessionItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (recalculated.compareTo(s.getSubtotal()) != 0 ||
                s.getTotalAmount().compareTo(s.getSubtotal()) != 0) {
            throw problem(HttpStatus.CONFLICT, "Checkout snapshot total mismatch");
        }
        Address a = addresses.findByIdAndUserId(s.getAddressId(), s.getUserId())
                .orElseThrow(() -> problem(HttpStatus.CONFLICT, "Shipping address no longer available"));
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
        o.setCodCharge(BigDecimal.ZERO);
        o.setDeliveryCharge(BigDecimal.ZERO);
        o.setDiscountAmount(BigDecimal.ZERO);
        o.setTaxAmount(BigDecimal.ZERO);
        o.setShippingFullName(a.getFullName());
        o.setShippingMobile(a.getMobile());
        o.setShippingAddressLine1(a.getAddressLine1());
        o.setShippingAddressLine2(a.getAddressLine2());
        o.setShippingLandmark(a.getLandmark());
        o.setShippingCity(a.getCity());
        o.setShippingState(a.getState());
        o.setShippingPostalCode(a.getPostalCode());
        o.setShippingCountry(a.getCountry());
        orders.saveAndFlush(o);
        List<OrderItem> copies = new ArrayList<>();
        for (CheckoutSessionItem item : snapshots) {
            OrderItem copy = new OrderItem();
            copy.setOrder(o);
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
        orderItems.saveAllAndFlush(copies);
        // Keep existing inventory lifecycle: RESERVED remains until order processing confirms stock.
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
        // Clear cart only if it still exactly matches paid snapshot; do not destroy later cart edits.
        Optional<Cart> cart = carts.findByUserIdForUpdate(s.getUserId());
        if (cart.isPresent()) {
            List<CartItem> current = cartItems.findByCartIdOrderByIdAsc(cart.get().getId());
            Map<Long, Integer> currentQty = new HashMap<>();
            Map<Long, Integer> paidQty = new HashMap<>();
            current.forEach(i -> currentQty.merge(i.getVariantId(), i.getQuantity(), Integer::sum));
            snapshots.forEach(i -> paidQty.merge(i.getVariantId(), i.getQuantity(), Integer::sum));
            if (currentQty.equals(paidQty)) {
                cartItems.deleteByCartId(cart.get().getId());
                cartItems.flush();
            }
        }
        return new CheckoutVerifyResponse(s.getId(), s.getStatus(), o.getId());
    }

    private boolean blank(String v) { return v == null || v.isBlank(); }
    private ResponseStatusException problem(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
