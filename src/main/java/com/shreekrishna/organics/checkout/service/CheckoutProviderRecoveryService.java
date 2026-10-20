
        package com.shreekrishna.organics.checkout.service;

import com.shreekrishna.organics.checkout.dto.CheckoutProviderRecoveryResponse;
import com.shreekrishna.organics.checkout.entity.CheckoutSession;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionRepository;
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

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Stage 5C: recover the Razorpay order association after PAYMENT_INITIATING.
 * Never creates provider orders, captures payments, cancels sessions or releases stock.
 * A lookup with zero results NEVER proves it is safe to retry/cancel.
 */
@Service
public class CheckoutProviderRecoveryService {
    private static final String INITIATING = "PAYMENT_INITIATING";
    private static final String PENDING = "PAYMENT_PENDING";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10;

    private final CheckoutSessionRepository sessions;
    private final UserRepository users;
    private final EntityManager em;
    private final TransactionTemplate transactions;
    private final String keyId;
    private final String keySecret;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public CheckoutProviderRecoveryService(CheckoutSessionRepository sessions,
                                           UserRepository users, EntityManager em, TransactionTemplate transactions,
                                           @Value("${razorpay.key.id}") String keyId,
                                           @Value("${razorpay.key.secret}") String keySecret) {
        this.sessions = sessions;
        this.users = users;
        this.em = em;
        this.transactions = transactions;
        this.keyId = keyId;
        this.keySecret = keySecret;
    }

    public CheckoutProviderRecoveryResponse preview(String email, String sessionId) {
        CheckoutSession s = owned(email, sessionId);
        if (hasLinkedOrder(s)) {
            return response(s, "ALREADY_LINKED", s.getRazorpayOrderId(), 1,
                    "Provider order already saved. Use payment reconciliation/finalization, not a new payment.");
        }
        if (!INITIATING.equals(s.getStatus()) || !Boolean.TRUE.equals(s.getStockReserved())) {
            return response(s, "NOT_ELIGIBLE", null, 0,
                    "Recovery is only available for a reserved PAYMENT_INITIATING session.");
        }
        Discovery d = discover(s);
        return describe(s, d);
    }

    public CheckoutProviderRecoveryResponse recover(String email, String sessionId) {
        CheckoutSession before = owned(email, sessionId);
        if (hasLinkedOrder(before)) {
            return response(before, "ALREADY_LINKED", before.getRazorpayOrderId(), 1,
                    "Provider order already saved. No new order was created.");
        }
        if (!INITIATING.equals(before.getStatus()) || !Boolean.TRUE.equals(before.getStockReserved())) {
            throw conflict("Only reserved PAYMENT_INITIATING sessions can be recovered");
        }
        // Remote HTTP outside transaction. Search by the EXACT receipt recorded at provider.
        Discovery found = discover(before);
        if (!found.complete || found.matches.size() != 1) {
            throw conflict("Provider association is uncertain (" + found.matches.size()
                    + " matches); session remains blocked for reconciliation");
        }
        Candidate candidate = found.matches.get(0);
        if (!"created".equals(candidate.status) && !"attempted".equals(candidate.status)
                && !"paid".equals(candidate.status)) {
            throw conflict("Unknown provider order state; manual reconciliation required");
        }
        return transactions.execute(tx -> {
            User user = findCustomer(email);
            em.lock(user, LockModeType.PESSIMISTIC_WRITE);
            CheckoutSession current = sessions.findOwnedForUpdate(sessionId, user.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout not found"));
            if (hasLinkedOrder(current) && candidate.id.equals(current.getRazorpayOrderId())) {
                return response(current, "ALREADY_LINKED", candidate.id, 1,
                        "Order association was already restored.");
            }
            if (!INITIATING.equals(current.getStatus()) || hasLinkedOrder(current)
                    || current.getRazorpayPaymentId() != null
                    || current.getFinalizedOrderId() != null
                    || !Boolean.TRUE.equals(current.getStockReserved())
                    || current.getTotalAmount().compareTo(before.getTotalAmount()) != 0
                    || !current.getCurrency().equals(before.getCurrency())) {
                throw conflict("Checkout changed during provider lookup; reconciliation required");
            }
            if (sessions.findByRazorpayOrderId(candidate.id).isPresent()) {
                throw conflict("Provider order already belongs to another checkout session");
            }
            current.setRazorpayOrderId(candidate.id);
            current.setStatus(PENDING);
            sessions.saveAndFlush(current);
            return response(current, "LINK_RESTORED", candidate.id, 1,
                    "Provider order restored. Do NOT start another charge. If paid, recover via verified webhook/finalizer.");
        });
    }

    /** Resolve the authenticated principal using email or MSG91 mobile. */
    private User findCustomer(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String value = identifier.trim();
        if (value.contains("@")) {
            return users.findByEmail(value.toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        }
        return users.findByMobile(value)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    private CheckoutSession owned(String email, String id) {
        Long userId = findCustomer(email)
                .getId();
        return sessions.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout not found"));
    }

    private Discovery discover(CheckoutSession s) {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay credentials unavailable; session remains blocked");
        }
        long expected;
        try {
            expected = s.getTotalAmount().movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw conflict("Invalid expected amount");
        }
        String receipt = URLEncoder.encode(s.getId(), StandardCharsets.UTF_8);
        String auth = Base64.getEncoder().encodeToString((keyId + ":" + keySecret)
                .getBytes(StandardCharsets.UTF_8));
        List<Candidate> matching = new ArrayList<>();
        boolean complete = false;
        for (int page = 0; page < MAX_PAGES; page++) {
            String url = "https://api.razorpay.com/v1/orders?receipt=" + receipt
                    + "&count=" + PAGE_SIZE + "&skip=" + (page * PAGE_SIZE);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Basic " + auth)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15)).GET().build();
            JSONObject body;
            try {
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Razorpay discovery failed; session remains blocked");
                }
                body = new JSONObject(res.body());
            } catch (ResponseStatusException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Razorpay discovery interrupted; session remains blocked", ex);
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Razorpay discovery unavailable; session remains blocked", ex);
            }
            JSONArray entries = body.optJSONArray("items");
            if (entries == null || !body.has("count") || body.optInt("count", -1) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Malformed Razorpay discovery response; session remains blocked");
            }
            for (int i = 0; i < entries.length(); i++) {
                JSONObject o = entries.optJSONObject(i);
                if (o == null) throw conflict("Malformed provider order record");
                // Razorpay receipt filtering is substring matching; verify exact equality.
                if (!s.getId().equals(o.optString("receipt"))) continue;
                if (!"INR".equals(o.optString("currency"))
                        || !"INR".equals(s.getCurrency())
                        || o.optLong("amount", -1) != expected
                        || o.optString("id", "").isBlank()) {
                    throw conflict("Provider order receipt matches but amount/currency conflicts");
                }
                matching.add(new Candidate(o.getString("id"), o.optString("status", "")));
            }
            if (entries.length() < PAGE_SIZE) {
                complete = true;
                break;
            }
        }
        return new Discovery(matching, complete);
    }

    private CheckoutProviderRecoveryResponse describe(CheckoutSession s, Discovery d) {
        if (!d.complete) {
            return response(s, "PAGINATION_UNRESOLVED", null, d.matches.size(),
                    "Provider listing too large; manual reconciliation required. Do not retry.");
        }
        if (d.matches.isEmpty()) {
            return response(s, "NO_MATCH_UNRESOLVED", null, 0,
                    "No order currently visible. A delayed provider response is still possible; do not retry, cancel or release stock.");
        }
        if (d.matches.size() != 1) {
            return response(s, "MULTIPLE_MATCHES", null, d.matches.size(),
                    "More than one provider order matches receipt; manual review required.");
        }
        Candidate c = d.matches.get(0);
        if (!"created".equals(c.status) && !"attempted".equals(c.status)
                && !"paid".equals(c.status)) {
            return response(s, "UNKNOWN_PROVIDER_STATE", c.id, 1,
                    "Unknown provider status; manual review required.");
        }
        return response(s, "EXACT_PROVIDER_MATCH", c.id, 1,
                "Matching provider order found; recover linkage without creating a new charge.");
    }

    private boolean hasLinkedOrder(CheckoutSession s) {
        return s.getRazorpayOrderId() != null && !s.getRazorpayOrderId().isBlank();
    }

    private CheckoutProviderRecoveryResponse response(CheckoutSession s, String assessment,
                                                      String providerId, int matchCount, String message) {
        return new CheckoutProviderRecoveryResponse(s.getId(), s.getStatus(), assessment,
                providerId, matchCount, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record Candidate(String id, String status) {}
    private record Discovery(List<Candidate> matches, boolean complete) {}
}