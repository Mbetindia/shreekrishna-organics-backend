package com.shreekrishna.organics.checkout.service;

import com.shreekrishna.organics.checkout.dto.CheckoutCancelResponse;
import com.shreekrishna.organics.checkout.dto.CheckoutVerifyResponse;
import com.shreekrishna.organics.checkout.entity.CheckoutSession;
import com.shreekrishna.organics.checkout.entity.CheckoutSessionItem;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionItemRepository;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionRepository;
import com.shreekrishna.organics.inventory.service.InventoryService;
import com.shreekrishna.organics.user.repository.UserRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Safely abandons a checkout session after online payment was started.
 *
 * Rules:
 * - FINALIZED / COD_FINALIZED sessions are never cancelled.
 * - If Razorpay reports a captured payment, finalize the online order instead.
 * - If Razorpay reports created/authorized/processing/unknown payment state, cancellation is blocked.
 * - Only when two consecutive provider scans show no live payment do we release the
 *   checkout reservation and mark the session ABANDONED.
 *
 * This endpoint is for "Cancel this checkout & continue shopping". It does not
 * clear the customer's current cart; it releases only the checkout-session stock
 * reservation so the customer can modify the cart and start a fresh checkout.
 */
@Service
public class CheckoutCancelService {
    private static final String CREATED = "CREATED";
    private static final String PAYMENT_PENDING = "PAYMENT_PENDING";
    private static final String FINALIZED = "FINALIZED";
    private static final String COD_FINALIZED = "COD_FINALIZED";
    private static final String ABANDONED = "ABANDONED";

    private final CheckoutSessionRepository sessions;
    private final CheckoutSessionItemRepository sessionItems;
    private final UserRepository users;
    private final InventoryService inventory;
    private final CheckoutPaymentFinalizationService finalizer;
    private final TransactionTemplate transactions;
    private final String keyId;
    private final String keySecret;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public CheckoutCancelService(
            CheckoutSessionRepository sessions,
            CheckoutSessionItemRepository sessionItems,
            UserRepository users,
            InventoryService inventory,
            CheckoutPaymentFinalizationService finalizer,
            TransactionTemplate transactions,
            @Value("${razorpay.key.id}") String keyId,
            @Value("${razorpay.key.secret}") String keySecret) {
        this.sessions = sessions;
        this.sessionItems = sessionItems;
        this.users = users;
        this.inventory = inventory;
        this.finalizer = finalizer;
        this.transactions = transactions;
        this.keyId = keyId;
        this.keySecret = keySecret;
    }

    public CheckoutCancelResponse cancelCheckout(String email, String sessionId) {
        CheckoutSession before = owned(email, sessionId);

        if (FINALIZED.equals(before.getStatus()) && before.getFinalizedOrderId() != null) {
            return reply(before, "ONLINE_ALREADY_FINALIZED", false,
                    "Online payment was already finalized. The order cannot be cancelled from checkout.");
        }

        if (COD_FINALIZED.equals(before.getStatus()) && before.getFinalizedOrderId() != null) {
            return reply(before, "COD_ALREADY_FINALIZED", false,
                    "A COD order was already created. Cancel it from the order flow if your policy allows cancellation.");
        }

        if (ABANDONED.equals(before.getStatus())
                && !Boolean.TRUE.equals(before.getStockReserved())
                && before.getFinalizedOrderId() == null) {
            return reply(before, "ALREADY_CANCELLED", true,
                    "This checkout was already cancelled. You can continue shopping.");
        }

        // Unstarted draft: no provider order, no reserved stock. Safe to abandon immediately.
        if (CREATED.equals(before.getStatus())
                && blank(before.getRazorpayOrderId())
                && blank(before.getRazorpayPaymentId())
                && !Boolean.TRUE.equals(before.getStockReserved())
                && before.getFinalizedOrderId() == null) {
            CheckoutCancelResponse result = transactions.execute(tx -> cancelLocked(email, sessionId, before, false));
            if (result == null) throw serverError("Unable to cancel checkout");
            return result;
        }

        if (!PAYMENT_PENDING.equals(before.getStatus())
                || !Boolean.TRUE.equals(before.getStockReserved())
                || blank(before.getRazorpayOrderId())
                || before.getTotalAmount() == null
                || !"INR".equals(before.getCurrency())) {
            throw conflict("Checkout cannot be cancelled until its payment state is reconciled");
        }

        long expectedPaise;
        try {
            expectedPaise = before.getTotalAmount().movePointRight(2).longValueExact();
        } catch (ArithmeticException ex) {
            throw conflict("Invalid checkout amount");
        }

        ProviderScan first = fetchPayments(before.getRazorpayOrderId(), expectedPaise);
        CheckoutCancelResponse resolved = handleCapturedOrUncertain(before, first, expectedPaise);
        if (resolved != null) return resolved;

        // Reduce the race window between popup dismissal and provider visibility.
        try {
            Thread.sleep(2000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Cancellation check was interrupted. Checkout remains active.", ex);
        }

        ProviderScan second = fetchPayments(before.getRazorpayOrderId(), expectedPaise);
        resolved = handleCapturedOrUncertain(before, second, expectedPaise);
        if (resolved != null) return resolved;

        // Both scans contain no captured/authorized/processing/unknown live payment.
        CheckoutCancelResponse result = transactions.execute(tx -> cancelLocked(email, sessionId, before, true));
        if (result == null) throw serverError("Unable to cancel checkout");
        return result;
    }

    private CheckoutCancelResponse handleCapturedOrUncertain(
            CheckoutSession session, ProviderScan scan, long expectedPaise) {
        if (scan.capturedCount() == 1 && !scan.uncertain()) {
            CheckoutVerifyResponse verified = finalizer.finalizeCapturedWebhook(
                    session.getRazorpayOrderId(),
                    scan.capturedPaymentId(),
                    expectedPaise,
                    "INR"
            );
            CheckoutSession after = sessions.findByIdAndUserId(session.getId(), session.getUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Checkout session not found"));
            return new CheckoutCancelResponse(
                    after.getId(),
                    after.getStatus(),
                    "ONLINE_PAYMENT_FINALIZED",
                    false,
                    verified.orderId(),
                    "A captured online payment was found, so the order was finalized instead of cancelling checkout."
            );
        }

        if (scan.capturedCount() > 1 || scan.uncertain()) {
            throw conflict(
                    "A Razorpay payment is captured, authorized, processing, created, or otherwise uncertain. "
                            + "Checkout cannot be cancelled yet. Check payment status again."
            );
        }

        return null;
    }

    private CheckoutCancelResponse cancelLocked(
            String email,
            String sessionId,
            CheckoutSession before,
            boolean releaseReservation) {

        Long userId = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"))
                .getId();

        CheckoutSession session = sessions.findOwnedForUpdate(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Checkout session not found"));

        if (FINALIZED.equals(session.getStatus()) && session.getFinalizedOrderId() != null) {
            return reply(session, "ONLINE_ALREADY_FINALIZED", false,
                    "Online payment was finalized while cancellation was being processed.");
        }
        if (COD_FINALIZED.equals(session.getStatus()) && session.getFinalizedOrderId() != null) {
            return reply(session, "COD_ALREADY_FINALIZED", false,
                    "COD order was created while cancellation was being processed.");
        }
        if (ABANDONED.equals(session.getStatus())
                && !Boolean.TRUE.equals(session.getStockReserved())
                && session.getFinalizedOrderId() == null) {
            return reply(session, "ALREADY_CANCELLED", true,
                    "This checkout was already cancelled.");
        }

        if (!Objects.equals(session.getRazorpayOrderId(), before.getRazorpayOrderId())
                || session.getFinalizedOrderId() != null) {
            throw conflict("Checkout changed during payment reconciliation. Check payment status again.");
        }

        if (releaseReservation) {
            if (!PAYMENT_PENDING.equals(session.getStatus()) || !Boolean.TRUE.equals(session.getStockReserved())) {
                throw conflict("Checkout reservation is no longer in a cancellable state");
            }

            List<CheckoutSessionItem> snapshots =
                    sessionItems.findBySession_IdOrderByIdAsc(session.getId());
            if (snapshots.isEmpty()) {
                throw conflict("Checkout session is empty");
            }

            for (CheckoutSessionItem item : snapshots) {
                if (item.getVariantId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                    throw conflict("Checkout contains an invalid reserved item");
                }
                inventory.releaseStock(item.getVariantId(), item.getQuantity());
            }

            session.setStockReserved(false);
        } else {
            if (!CREATED.equals(session.getStatus())
                    || Boolean.TRUE.equals(session.getStockReserved())
                    || !blank(session.getRazorpayOrderId())
                    || !blank(session.getRazorpayPaymentId())) {
                throw conflict("Checkout is no longer an unstarted draft");
            }
        }

        session.setStatus(ABANDONED);
        sessions.saveAndFlush(session);

        return reply(session, "CHECKOUT_CANCELLED", true,
                "Checkout cancelled. Reserved stock was released and your current cart was left unchanged.");
    }

    private ProviderScan fetchPayments(String orderId, long expectedPaise) {
        if (blank(keyId) || blank(keySecret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay credentials unavailable. Checkout remains active.");
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
                            "Razorpay payment lookup failed. Checkout remains active.");
                }
                payload = new JSONObject(response.body());
            } catch (ResponseStatusException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Razorpay lookup interrupted. Checkout remains active.", ex);
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Razorpay lookup unavailable. Checkout remains active.", ex);
            }

            JSONArray entries = payload.optJSONArray("items");
            if (entries == null || !payload.has("count")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Malformed Razorpay response. Checkout remains active.");
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
            throw conflict("Provider payment listing is incomplete. Checkout remains active.");
        }

        return new ProviderScan(captures, capturedId, uncertain);
    }

    private CheckoutSession owned(String email, String sessionId) {
        Long userId = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"))
                .getId();
        return sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Checkout session not found"));
    }

    private CheckoutCancelResponse reply(
            CheckoutSession session,
            String assessment,
            boolean cancelled,
            String message) {
        return new CheckoutCancelResponse(
                session.getId(),
                session.getStatus(),
                assessment,
                cancelled,
                session.getFinalizedOrderId(),
                message
        );
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException serverError(String message) {
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    private record ProviderScan(int capturedCount, String capturedPaymentId, boolean uncertain) {}
}
