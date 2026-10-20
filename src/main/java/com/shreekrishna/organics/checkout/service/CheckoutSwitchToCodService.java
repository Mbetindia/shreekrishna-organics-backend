package com.shreekrishna.organics.checkout.service;

import com.shreekrishna.organics.cart.entity.Cart;
import com.shreekrishna.organics.cart.entity.CartItem;
import com.shreekrishna.organics.cart.repository.CartItemRepository;
import com.shreekrishna.organics.cart.repository.CartRepository;
import com.shreekrishna.organics.checkout.dto.CheckoutSwitchToCodResponse;
import com.shreekrishna.organics.checkout.dto.CheckoutVerifyResponse;
import com.shreekrishna.organics.checkout.entity.CheckoutSession;
import com.shreekrishna.organics.checkout.entity.CheckoutSessionItem;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionItemRepository;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionRepository;
import com.shreekrishna.organics.order.entity.*;
import com.shreekrishna.organics.order.repository.OrderItemRepository;
import com.shreekrishna.organics.order.repository.OrderRepository;
import com.shreekrishna.organics.user.entity.User;
import com.shreekrishna.organics.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Converts an unresolved online checkout into ONE COD order using the already
 * reserved checkout-session stock. It never releases/re-reserves stock and it
 * never creates a second checkout session.
 *
 * Before conversion it checks Razorpay. A captured payment is finalized online;
 * an authorized/processing payment keeps COD blocked. If no live payment exists,
 * the same checkout snapshot is converted to COD.
 *
 * A late capture after COD conversion is handled by CheckoutPaymentFinalizationService,
 * which converts the SAME order from COD/PENDING to RAZORPAY/PAID instead of
 * creating a duplicate order.
 */
@Service
public class CheckoutSwitchToCodService {
    private static final String PAYMENT_PENDING = "PAYMENT_PENDING";
    private static final String COD_FINALIZED = "COD_FINALIZED";

    private final CheckoutSessionRepository sessions;
    private final CheckoutSessionItemRepository sessionItems;
    private final UserRepository users;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final CheckoutPaymentFinalizationService finalizer;
    private final com.shreekrishna.organics.inventory.service.InventoryService inventoryService;
    private final EntityManager em;
    private final TransactionTemplate transactions;
    private final String keyId;
    private final String keySecret;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public CheckoutSwitchToCodService(
            CheckoutSessionRepository sessions,
            CheckoutSessionItemRepository sessionItems,
            UserRepository users,
            OrderRepository orders,
            OrderItemRepository orderItems,
            CartRepository carts,
            CartItemRepository cartItems,
            CheckoutPaymentFinalizationService finalizer,
            com.shreekrishna.organics.inventory.service.InventoryService inventoryService,
            EntityManager em,
            TransactionTemplate transactions,
            @Value("${razorpay.key.id}") String keyId,
            @Value("${razorpay.key.secret}") String keySecret) {
        this.sessions = sessions;
        this.sessionItems = sessionItems;
        this.users = users;
        this.orders = orders;
        this.orderItems = orderItems;
        this.carts = carts;
        this.cartItems = cartItems;
        this.finalizer = finalizer;
        this.inventoryService = inventoryService;
        this.em = em;
        this.transactions = transactions;
        this.keyId = keyId;
        this.keySecret = keySecret;
    }

    public CheckoutSwitchToCodResponse switchToCod(String email, String sessionId) {
        CheckoutSession before = owned(email, sessionId);

        if (COD_FINALIZED.equals(before.getStatus()) && before.getFinalizedOrderId() != null) {
            return response(before, "COD_ALREADY_CREATED", before.getFinalizedOrderId(), true,
                    "COD order has already been created from this checkout session.");
        }
        if ("FINALIZED".equals(before.getStatus()) && before.getFinalizedOrderId() != null) {
            return response(before, "ONLINE_ALREADY_FINALIZED", before.getFinalizedOrderId(), false,
                    "Online payment was already finalized. Do not create a COD order.");
        }
        if (!PAYMENT_PENDING.equals(before.getStatus())
                || !Boolean.TRUE.equals(before.getStockReserved())
                || blank(before.getRazorpayOrderId())
                || before.getTotalAmount() == null
                || !"INR".equals(before.getCurrency())) {
            throw conflict("Checkout cannot be switched to COD until its online payment state is safely reconciled");
        }

        long expectedPaise;
        try {
            expectedPaise = before.getTotalAmount().movePointRight(2).longValueExact();
        } catch (ArithmeticException ex) {
            throw conflict("Invalid checkout amount");
        }

        ProviderScan scan = fetchPayments(before.getRazorpayOrderId(), expectedPaise);

        if (scan.capturedCount() == 1 && !scan.uncertain()) {
            CheckoutVerifyResponse verified = finalizer.finalizeCapturedWebhook(
                    before.getRazorpayOrderId(), scan.capturedPaymentId(), expectedPaise, "INR");
            CheckoutSession after = sessions.findByIdAndUserId(sessionId, before.getUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout session not found"));
            return response(after, "ONLINE_PAYMENT_FINALIZED", verified.orderId(), false,
                    "A captured online payment was found, so the existing checkout was finalized online instead of COD.");
        }

        if (scan.capturedCount() > 1 || scan.uncertain()) {
            throw conflict("An online payment is captured, authorized, processing, or otherwise uncertain. COD remains blocked.");
        }

        CheckoutSwitchToCodResponse result = transactions.execute(tx -> createCodOrder(email, sessionId, before));
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to create COD order");
        }
        return result;
    }

    /** Direct COD checkout from a CREATED Buy Now draft; no Razorpay order exists. */
    public CheckoutSwitchToCodResponse placeBuyNowCod(String identity, String sessionId) {
        CheckoutSession before = owned(identity, sessionId);
        if (!"BUY_NOW".equals(before.getCheckoutSource())) {
            throw conflict("Direct session COD is available only for Buy Now");
        }
        CheckoutSwitchToCodResponse result = transactions.execute(tx ->
                createCodOrder(identity, sessionId, before, true));
        if (result == null) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unable to create COD order");
        return result;
    }

    private CheckoutSwitchToCodResponse createCodOrder(String email, String sessionId, CheckoutSession before) {
        return createCodOrder(email, sessionId, before, false);
    }

    private CheckoutSwitchToCodResponse createCodOrder(String email, String sessionId,
                                                        CheckoutSession before, boolean directBuyNow) {
        User user = findAuthenticatedCustomer(email);
        em.lock(user, LockModeType.PESSIMISTIC_WRITE);

        CheckoutSession session = sessions.findOwnedForUpdate(sessionId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout session not found"));

        if (COD_FINALIZED.equals(session.getStatus()) && session.getFinalizedOrderId() != null) {
            return response(session, "COD_ALREADY_CREATED", session.getFinalizedOrderId(), true,
                    "COD order has already been created.");
        }
        if ("FINALIZED".equals(session.getStatus()) && session.getFinalizedOrderId() != null) {
            return response(session, "ONLINE_ALREADY_FINALIZED", session.getFinalizedOrderId(), false,
                    "Online payment was finalized while COD conversion was being processed.");
        }
        if (!(directBuyNow
                ? "CREATED".equals(session.getStatus())
                    && "BUY_NOW".equals(session.getCheckoutSource())
                    && !Boolean.TRUE.equals(session.getStockReserved())
                    && session.getRazorpayOrderId() == null
                    && session.getRazorpayPaymentId() == null
                    && session.getFinalizedOrderId() == null
                    && session.getExpiresAt() != null
                    && session.getExpiresAt().isAfter(java.time.LocalDateTime.now())
                : PAYMENT_PENDING.equals(session.getStatus())
                    && Boolean.TRUE.equals(session.getStockReserved()))
                || !Objects.equals(session.getRazorpayOrderId(), before.getRazorpayOrderId())
                || session.getTotalAmount() == null
                || before.getTotalAmount() == null
                || session.getTotalAmount().compareTo(before.getTotalAmount()) != 0
                || !Objects.equals(session.getDeliveryCharge(), before.getDeliveryCharge())
                || !Objects.equals(session.getCurrency(), before.getCurrency())) {
            throw conflict("Checkout changed during payment reconciliation. Check payment status again.");
        }

        List<CheckoutSessionItem> snapshots = sessionItems.findBySession_IdOrderByIdAsc(session.getId());
        if (snapshots.isEmpty()) throw conflict("Checkout session is empty");

        BigDecimal recalculated = BigDecimal.ZERO;
        BigDecimal additionalTax = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO.setScale(2);
        boolean legacy = snapshots.stream().allMatch(item ->
                item.getTaxAmount() == null
                        && item.getGstRate() == null
                        && item.getPriceIncludesGst() == null);

        for (CheckoutSessionItem item : snapshots) {
            if (item.getLineTotal() == null || item.getLineTotal().signum() < 0) {
                throw conflict("Invalid checkout item amount");
            }
            recalculated = recalculated.add(item.getLineTotal());
            if (legacy) continue;
            if (item.getTaxAmount() == null || item.getGstRate() == null
                    || item.getPriceIncludesGst() == null
                    || item.getTaxAmount().signum() < 0
                    || item.getGstRate().signum() < 0
                    || item.getGstRate().compareTo(new BigDecimal("100")) > 0) {
                throw conflict("Incomplete GST checkout snapshot; COD conversion blocked");
            }
            totalTax = totalTax.add(item.getTaxAmount());
            if (!item.getPriceIncludesGst()) {
                additionalTax = additionalTax.add(item.getTaxAmount());
            }
        }

        BigDecimal deliveryCharge = session.getDeliveryCharge();
        if (session.getSubtotal() == null || session.getTotalAmount() == null
                || deliveryCharge == null || deliveryCharge.signum() < 0
                || recalculated.compareTo(session.getSubtotal()) != 0
                || session.getSubtotal().signum() <= 0
                || session.getTotalAmount().compareTo(
                session.getSubtotal().add(deliveryCharge).add(additionalTax)) != 0) {
            throw conflict("Checkout snapshot total mismatch");
        }
        validateShipping(session);

        if (directBuyNow) {
            // Reserve stock in this same DB transaction before transferring to the COD order.
            snapshots.stream()
                    .sorted(Comparator.comparing(CheckoutSessionItem::getVariantId))
                    .forEach(item -> inventoryService.reserveStock(item.getVariantId(), item.getQuantity()));
            session.setStockReserved(true);
        }

        Order order = new Order();
        order.setOrderNumber("SKO-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        order.setUserId(session.getUserId());
        order.setAddressId(session.getAddressId());
        order.setStatus(OrderStatus.PLACED);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setStockState(StockState.RESERVED);
        order.setCurrency(session.getCurrency());
        order.setSubtotal(session.getSubtotal());
        order.setDeliveryCharge(deliveryCharge);
        order.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        order.setTaxAmount(totalTax);

        BigDecimal codCharge = session.getSubtotal()
                .multiply(new BigDecimal("0.05"))
                .setScale(2, RoundingMode.HALF_UP);
        order.setCodCharge(codCharge);
        order.setTotalAmount(session.getTotalAmount().add(codCharge));

        order.setShippingFullName(session.getShippingFullName());
        order.setShippingMobile(session.getShippingMobile());
        order.setShippingAddressLine1(session.getShippingAddressLine1());
        order.setShippingAddressLine2(session.getShippingAddressLine2());
        order.setShippingLandmark(session.getShippingLandmark());
        order.setShippingCity(session.getShippingCity());
        order.setShippingState(session.getShippingState());
        order.setShippingPostalCode(session.getShippingPostalCode());
        order.setShippingCountry(session.getShippingCountry());
        orders.saveAndFlush(order);

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
        orderItems.saveAllAndFlush(copies);

        // Reservation is transferred from the checkout session to this COD order.
        // Do NOT release and reserve again.
        session.setFinalizedOrderId(order.getId());
        session.setStatus(COD_FINALIZED);
        sessions.saveAndFlush(session);

        if (!"BUY_NOW".equals(session.getCheckoutSource())) {
            removePurchasedItemsFromCart(session.getUserId(), snapshots);
        }

        return response(session, "COD_ORDER_CREATED", order.getId(), true,
                "Online checkout was converted to Cash on Delivery. The same reserved stock and checkout snapshot were used.");
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

    private ProviderScan fetchPayments(String orderId, long expectedPaise) {
        if (blank(keyId) || blank(keySecret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay credentials unavailable. COD switch remains blocked.");
        }

        String auth = Base64.getEncoder().encodeToString(
                (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
        int captures = 0;
        String capturedId = null;
        boolean uncertain = false;
        boolean completed = false;

        for (int page = 0; page < 10; page++) {
            String url = "https://api.razorpay.com/v1/orders/"
                    + URLEncoder.encode(orderId, StandardCharsets.UTF_8)
                    + "/payments?count=100&skip=" + (page * 100);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Basic " + auth)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            JSONObject payload;
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Razorpay payment lookup failed. COD switch remains blocked.");
                }
                payload = new JSONObject(response.body());
            } catch (ResponseStatusException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Razorpay lookup interrupted. COD switch remains blocked.", ex);
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Razorpay lookup unavailable. COD switch remains blocked.", ex);
            }

            JSONArray entries = payload.optJSONArray("items");
            if (entries == null || !payload.has("count")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Malformed Razorpay response. COD switch remains blocked.");
            }

            for (int i = 0; i < entries.length(); i++) {
                JSONObject payment = entries.optJSONObject(i);
                if (payment == null) throw conflict("Invalid Razorpay payment entry");
                if (!orderId.equals(payment.optString("order_id"))) {
                    throw conflict("Razorpay returned a payment for another order");
                }

                String status = payment.optString("status", "");
                if ("captured".equals(status)) {
                    if (!"INR".equals(payment.optString("currency"))
                            || payment.optLong("amount", -1) != expectedPaise
                            || blank(payment.optString("id", ""))) {
                        throw conflict("Captured payment amount/currency mismatch");
                    }
                    captures++;
                    capturedId = payment.getString("id");
                } else if (!"failed".equals(status) && !"refunded".equals(status)) {
                    // created / authorized / processing / unknown can still become payable.
                    uncertain = true;
                }
            }

            if (entries.length() < 100) {
                completed = true;
                break;
            }
        }

        if (!completed) {
            throw conflict("Provider payment listing is incomplete. COD switch remains blocked.");
        }
        return new ProviderScan(captures, capturedId, uncertain);
    }

    /**
     * Resolve the authenticated principal for both legacy email and mobile-OTP customers.
     * Keep the same enabled CUSTOMER-only rule used by CheckoutDraftService.
     */
    private User findAuthenticatedCustomer(String identity) {
        if (identity == null || identity.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String principal = identity.trim();
        return (principal.contains("@")
                ? users.findByEmail(principal.toLowerCase(Locale.ROOT))
                : users.findByMobile(principal))
                .filter(user -> user.isEnabled()
                        && user.getRole() != null
                        && "CUSTOMER".equals(user.getRole().name()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    private CheckoutSession owned(String email, String sessionId) {
        Long userId = findAuthenticatedCustomer(email).getId();
        return sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout session not found"));
    }

    private CheckoutSwitchToCodResponse response(
            CheckoutSession session,
            String assessment,
            Long orderId,
            boolean switchedToCod,
            String message) {
        return new CheckoutSwitchToCodResponse(
                session.getId(), assessment, session.getStatus(), orderId, switchedToCod, message);
    }

    private void validateShipping(CheckoutSession s) {
        if (blank(s.getShippingFullName()) || blank(s.getShippingMobile())
                || blank(s.getShippingAddressLine1()) || blank(s.getShippingCity())
                || blank(s.getShippingState()) || blank(s.getShippingPostalCode())
                || blank(s.getShippingCountry())) {
            throw conflict("Checkout shipping snapshot is missing; COD conversion cannot continue");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record ProviderScan(int capturedCount, String capturedPaymentId, boolean uncertain) {}
}
