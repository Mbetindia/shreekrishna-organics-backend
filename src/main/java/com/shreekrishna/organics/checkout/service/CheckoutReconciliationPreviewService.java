package com.shreekrishna.organics.checkout.service;

import com.shreekrishna.organics.checkout.dto.CheckoutReconciliationPreview;
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
 * STAGE 3D-B DIAGNOSTIC ONLY. Does NOT cancel, expire, finalize, mutate inventory,
 * alter orders, or authorize a new COD/online payment.
 */
@Service
public class CheckoutReconciliationPreviewService {
    private final CheckoutSessionRepository sessions;
    private final UserRepository users;
    private final String keyId;
    private final String keySecret;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public CheckoutReconciliationPreviewService(
            CheckoutSessionRepository sessions,
            UserRepository users,
            @Value("${razorpay.key.id}") String keyId,
            @Value("${razorpay.key.secret}") String keySecret) {
        this.sessions = sessions;
        this.users = users;
        this.keyId = keyId;
        this.keySecret = keySecret;
    }

    public CheckoutReconciliationPreview inspect(String email, String sessionId) {
        Long userId = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"))
                .getId();
        CheckoutSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout session not found"));
        if ("FINALIZED".equals(session.getStatus()) && session.getFinalizedOrderId() != null) {
            return result(session, "ALREADY_FINALIZED", 0, session.getRazorpayPaymentId(),
                    "Order already finalized. No new payment or order should be created.");
        }
        if (session.getRazorpayOrderId() == null || session.getRazorpayOrderId().isBlank()) {
            return result(session, "NO_PROVIDER_ORDER", 0, null,
                    "No Razorpay order linked. This is informational, not approval to create COD.");
        }
        if (keyId.isBlank() || keySecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Razorpay credentials unavailable");
        }
        // Razorpay Orders API: fetch the payments associated with an order.
        String encoded = URLEncoder.encode(session.getRazorpayOrderId(), StandardCharsets.UTF_8);
        URI url = URI.create("https://api.razorpay.com/v1/orders/" + encoded + "/payments?count=100&skip=0");
        String auth = Base64.getEncoder().encodeToString(
                (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(url)
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Basic " + auth)
                .header("Accept", "application/json")
                .GET().build();
        final JSONObject json;
        try {
            HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Razorpay status lookup failed; payment remains unresolved");
            }
            json = new JSONObject(response.body());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay status lookup interrupted", ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay status lookup unavailable; payment remains unresolved", ex);
        }
        JSONArray items = json.optJSONArray("items");
        if (items == null || !json.has("count")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid Razorpay status response");
        }
        // Never claim a clean result from a partial result set.
        if (json.optInt("count", -1) > items.length()) {
            return result(session, "UNRESOLVED_PAGINATION", items.length(), null,
                    "More payment attempts exist than were inspected; manual/provider reconciliation required.");
        }
        String capturedId = null;
        boolean authorized = false;
        boolean inconsistent = false;
        long expectedPaise;
        try {
            expectedPaise = session.getTotalAmount().movePointRight(2).longValueExact();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid checkout amount");
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject p = items.optJSONObject(i);
            if (p == null) { inconsistent = true; continue; }
            if (!session.getRazorpayOrderId().equals(p.optString("order_id"))) {
                inconsistent = true; continue;
            }
            String state = p.optString("status", "");
            if ("captured".equals(state)) {
                if (!"INR".equals(p.optString("currency"))
                        || !"INR".equals(session.getCurrency())
                        || p.optLong("amount", -1) != expectedPaise) {
                    inconsistent = true;
                } else if (capturedId != null && !capturedId.equals(p.optString("id"))) {
                    inconsistent = true;
                } else {
                    capturedId = p.optString("id", "");
                }
            } else if ("authorized".equals(state)) {
                authorized = true;
            } else if (!"failed".equals(state) && !"refunded".equals(state)) {
                inconsistent = true;
            }
        }
        if (inconsistent) {
            return result(session, "UNRESOLVED_CONFLICT", items.length(), capturedId,
                    "Payment attempts have inconsistent or unknown details. Do not retry or release stock.");
        }
        if (capturedId != null) {
            return result(session, "CAPTURED_NOT_FINALIZED", items.length(), capturedId,
                    "Captured payment exists. Run signed verification/webhook finalization; do not pay again.");
        }
        if (authorized) {
            return result(session, "AUTHORIZED_NOT_CAPTURED", items.length(), null,
                    "Payment authorized; it may still be captured. Do not cancel or switch to COD.");
        }
        return result(session, "NO_CAPTURED_PAYMENT_OBSERVED", items.length(), null,
                "Snapshot only: a late capture remains possible. NOT clearance for cancellation, stock release or COD.");
    }

    private CheckoutReconciliationPreview result(CheckoutSession s, String assessment,
            int attempts, String capturedId, String guidance) {
        return new CheckoutReconciliationPreview(s.getId(), s.getStatus(),
                s.getRazorpayOrderId(), assessment, attempts,
                capturedId, s.getFinalizedOrderId(), guidance);
    }
}
