package com.shreekrishna.organics.checkout.service;

import com.shreekrishna.organics.checkout.dto.CheckoutCaptureRecoveryResponse;
import com.shreekrishna.organics.checkout.dto.CheckoutVerifyResponse;
import com.shreekrishna.organics.checkout.entity.CheckoutSession;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionRepository;
import com.shreekrishna.organics.user.repository.UserRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Stage 5D: read remote payment outcomes without a browser signature, then finalize
 * only when the Razorpay API independently confirms a single matching capture.
 * Never creates, captures, refunds or cancels a payment. Never releases stock.
 */
@Service
public class CheckoutCaptureRecoveryService {
    private final CheckoutSessionRepository sessions;
    private final UserRepository users;
    private final CheckoutPaymentFinalizationService finalizer;
    private final String keyId;
    private final String keySecret;
    private final boolean recoveryEnabled;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public CheckoutCaptureRecoveryService(CheckoutSessionRepository sessions,
            UserRepository users, CheckoutPaymentFinalizationService finalizer,
            @Value("${razorpay.key.id}") String keyId,
            @Value("${razorpay.key.secret}") String keySecret,
            @Value("${checkout.capture-recovery-enabled:false}") boolean recoveryEnabled) {
        this.sessions = sessions;
        this.users = users;
        this.finalizer = finalizer;
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.recoveryEnabled = recoveryEnabled;
    }

    public CheckoutCaptureRecoveryResponse preview(String email, String sessionId) {
        CheckoutSession session = owned(email, sessionId);
        if ("FINALIZED".equals(session.getStatus())) {
            return outcome(session, "ALREADY_FINALIZED", "Order already finalized; no further payment needed");
        }
        if (session.getRazorpayOrderId() == null || session.getRazorpayOrderId().isBlank()) {
            return outcome(session, "NO_LINKED_RAZORPAY_ORDER",
                    "No linked provider order; use Stage 5C initiation recovery if needed");
        }
        if (!"PAYMENT_PENDING".equals(session.getStatus())) {
            return outcome(session, "NOT_READY",
                    "Payment is not in a finalizable state; reconcile before retrying or switching to COD");
        }
        return outcome(session, "READY_FOR_PROVIDER_CHECK",
                "Provider order is linked. Use guarded POST recovery to check captured payments");
    }

    public CheckoutCaptureRecoveryResponse recover(String email, String sessionId) {
        if (!recoveryEnabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Captured-payment recovery disabled until end-to-end tests are approved");
        }
        CheckoutSession session = owned(email, sessionId);
        if ("FINALIZED".equals(session.getStatus())) {
            return outcome(session, "ALREADY_FINALIZED", "Order already finalized");
        }
        if (!"PAYMENT_PENDING".equals(session.getStatus())
                || !Boolean.TRUE.equals(session.getStockReserved())
                || session.getRazorpayOrderId() == null || session.getRazorpayOrderId().isBlank()
                || session.getTotalAmount() == null || !"INR".equals(session.getCurrency())) {
            throw conflict("Checkout requires manual reconciliation; do not create another charge");
        }
        String orderId = session.getRazorpayOrderId();
        long expectedPaise = session.getTotalAmount().movePointRight(2).longValueExact();
        ProviderScan scan = fetchPayments(orderId, expectedPaise);
        if (scan.capturedCount == 0) {
            return outcome(session, "NO_CAPTURE_OBSERVED",
                    "No captured payment currently visible. This does not authorize COD, retry or stock release");
        }
        if (scan.capturedCount != 1 || scan.uncertain) {
            throw conflict("Multiple or inconsistent payment outcomes; manual reconciliation required");
        }
        // This method performs atomic idempotent finalization under the session row lock.
        // It revalidates provider order, amount and currency before creating an order.
        CheckoutVerifyResponse verified = finalizer.finalizeCapturedWebhook(
                orderId, scan.paymentId, expectedPaise, "INR");
        return new CheckoutCaptureRecoveryResponse(sessionId, "ORDER_FINALIZED",
                verified.status(), verified.orderId(),
                "Provider-captured payment reconciled and existing/new order confirmed");
    }

    private ProviderScan fetchPayments(String orderId, long expectedPaise) {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay credentials unavailable; checkout remains blocked");
        }
        String auth = Base64.getEncoder().encodeToString(
                (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
        int captures = 0;
        String capturedId = null;
        boolean uncertain = false;
        // Fetch all available pages. Never infer absence from a partial provider response.
        boolean completed = false;
        for (int page = 0; page < 10; page++) {
            String url = "https://api.razorpay.com/v1/orders/"
                    + URLEncoder.encode(orderId, StandardCharsets.UTF_8)
                    + "/payments?count=100&skip=" + (page * 100);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Basic " + auth)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15)).GET().build();
            JSONObject payload;
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Razorpay payment lookup failed; checkout remains blocked");
                }
                payload = new JSONObject(response.body());
            } catch (ResponseStatusException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Provider lookup interrupted; checkout remains blocked", ex);
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Provider lookup unavailable; checkout remains blocked", ex);
            }
            JSONArray items = payload.optJSONArray("items");
            if (items == null || !payload.has("count")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Malformed Razorpay response; checkout remains blocked");
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject payment = items.optJSONObject(i);
                if (payment == null) throw conflict("Invalid Razorpay payment entry");
                if (!orderId.equals(payment.optString("order_id"))) {
                    throw conflict("Razorpay returned payment for another order");
                }
                String status = payment.optString("status");
                if ("captured".equals(status)) {
                    if (!"INR".equals(payment.optString("currency"))
                            || payment.optLong("amount", -1) != expectedPaise
                            || payment.optString("id", "").isBlank()) {
                        throw conflict("Captured payment amount/currency mismatch");
                    }
                    captures++;
                    capturedId = payment.getString("id");
                } else if (!"failed".equals(status) && !"refunded".equals(status)) {
                    // An authorized/processing payment can still capture later.
                    uncertain = true;
                }
            }
            if (items.length() < 100) {
                completed = true;
                break;
            }
        }
        if (!completed) throw conflict("Provider payments listing incomplete; manual reconciliation required");
        return new ProviderScan(captures, capturedId, uncertain);
    }

    private CheckoutSession owned(String email, String sessionId) {
        Long userId = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"))
                .getId();
        return sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout not found"));
    }

    private CheckoutCaptureRecoveryResponse outcome(CheckoutSession s, String assessment, String message) {
        return new CheckoutCaptureRecoveryResponse(s.getId(), assessment, s.getStatus(),
                s.getFinalizedOrderId(), message);
    }

    private ResponseStatusException conflict(String msg) {
        return new ResponseStatusException(HttpStatus.CONFLICT, msg);
    }

    private record ProviderScan(int capturedCount, String paymentId, boolean uncertain) {}
}
