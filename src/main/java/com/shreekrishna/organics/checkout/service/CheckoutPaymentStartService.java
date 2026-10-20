package com.shreekrishna.organics.checkout.service;

import com.razorpay.RazorpayException;
import com.shreekrishna.organics.checkout.dto.CheckoutPaymentResponse;
import com.shreekrishna.organics.checkout.entity.CheckoutSession;
import com.shreekrishna.organics.checkout.entity.CheckoutSessionItem;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionItemRepository;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionRepository;
import com.shreekrishna.organics.inventory.service.InventoryService;
import com.shreekrishna.organics.payment.service.RazorpayService;
import com.shreekrishna.organics.user.entity.User;
import com.shreekrishna.organics.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Stage 5B: persist a durable initiation marker BEFORE calling Razorpay.
 * A crash or uncertain provider response intentionally leaves the session blocked.
 * This class DOES NOT implement automatic provider-order discovery or release stock.
 */
@Service
public class CheckoutPaymentStartService {
    public static final String INITIATING = "PAYMENT_INITIATING";
    public static final String PENDING = "PAYMENT_PENDING";

    private final CheckoutSessionRepository sessions;
    private final CheckoutSessionItemRepository items;
    private final UserRepository users;
    private final EntityManager em;
    private final InventoryService inventory;
    private final RazorpayService razorpay;
    private final TransactionTemplate transactions;
    private final boolean enabled;

    public CheckoutPaymentStartService(CheckoutSessionRepository sessions,
                                       CheckoutSessionItemRepository items, UserRepository users,
                                       EntityManager em, InventoryService inventory, RazorpayService razorpay,
                                       TransactionTemplate transactions,
                                       @Value("${checkout.online-payments-enabled:false}") boolean enabled) {
        this.sessions = sessions;
        this.items = items;
        this.users = users;
        this.em = em;
        this.inventory = inventory;
        this.razorpay = razorpay;
        this.transactions = transactions;
        this.enabled = enabled;
    }

    public CheckoutPaymentResponse start(String identifier, String sessionId) throws RazorpayException {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "New checkout-session payments are disabled until finalization and recovery tests pass");
        }

        // First transaction commits stock reservations and a durable marker.
        // No external HTTP calls are made while locks are held.
        Preparation preparation = transactions.execute(status -> prepare(identifier, sessionId));
        if (preparation == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Payment preparation failed");
        }
        if (preparation.reuse()) {
            return getStatus(identifier, sessionId);
        }

        // Razorpay is called OUTSIDE the database transaction.
        // If this fails, times out, or the process crashes, PAYMENT_INITIATING
        // remains in the DB. NEVER automatically start a new provider order.
        com.razorpay.Order providerOrder = razorpay.createRazorpayOrder(
                preparation.amount(), preparation.sessionId());
        String razorpayOrderId = providerOrder.get("id");
        if (razorpayOrderId == null || razorpayOrderId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Razorpay returned an invalid order ID; reconciliation required");
        }

        // Second transaction commits association. Failure retains INITIATING,
        // which is intentionally blocked until manual/automated reconciliation.
        CheckoutPaymentResponse result = transactions.execute(status -> {
            CheckoutSession session = sessions.findOwnedForUpdate(sessionId, preparation.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Checkout session not found"));
            if (!INITIATING.equals(session.getStatus())
                    || session.getRazorpayOrderId() != null
                    || !Boolean.TRUE.equals(session.getStockReserved())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Payment initiation state changed; reconciliation required");
            }
            session.setRazorpayOrderId(razorpayOrderId);
            session.setStatus(PENDING);
            sessions.saveAndFlush(session);
            return response(session);
        });
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Provider order created but association failed; reconciliation required");
        }
        return result;
    }

    private Preparation prepare(String identifier, String sessionId) {
        User user = findCustomer(identifier);
        em.lock(user, LockModeType.PESSIMISTIC_WRITE);
        Long userId = user.getId();
        CheckoutSession session = sessions.findOwnedForUpdate(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Checkout session not found"));

        if (PENDING.equals(session.getStatus())
                && session.getRazorpayOrderId() != null
                && !session.getRazorpayOrderId().isBlank()) {
            return new Preparation(sessionId, userId, session.getTotalAmount(), true);
        }
        if (INITIATING.equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment initiation may already have reached Razorpay; reconcile before retrying");
        }
        if (!"CREATED".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout session not eligible");
        }
        if (session.getRazorpayOrderId() != null || session.getRazorpayPaymentId() != null
                || Boolean.TRUE.equals(session.getStockReserved())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Checkout has inconsistent payment/stock state");
        }
        if (!session.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Checkout draft expired; create a new draft");
        }
        if (session.getTotalAmount() == null || session.getTotalAmount().signum() <= 0
                || !"INR".equals(session.getCurrency())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid checkout amount/currency");
        }
        if (sessions.existsByUserIdAndStatusInAndIdNot(userId,
                List.of(INITIATING, PENDING), sessionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Another online checkout requires reconciliation");
        }
        List<CheckoutSessionItem> snapshots = items.findBySession_IdOrderByIdAsc(sessionId);
        if (snapshots.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout has no items");
        }
        snapshots.stream().sorted(Comparator.comparing(CheckoutSessionItem::getVariantId))
                .forEach(i -> inventory.reserveStock(i.getVariantId(), i.getQuantity()));
        session.setStockReserved(true);
        session.setStatus(INITIATING);
        sessions.saveAndFlush(session);
        return new Preparation(sessionId, userId, session.getTotalAmount(), false);
    }

    public CheckoutPaymentResponse status(String identifier, String sessionId) {
        return getStatus(identifier, sessionId);
    }

    private CheckoutPaymentResponse getStatus(String identifier, String sessionId) {
        Long userId = findCustomer(identifier).getId();
        CheckoutSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Checkout session not found"));
        return response(session);
    }

    // Resolve the authenticated JWT principal by email OR mobile number.
    // Never trust a client-provided identifier in place of the authenticated principal.
    private User findCustomer(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String value = identifier.trim();
        if (value.contains("@")) {
            return users.findByEmail(value.toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Customer not found"));
        }

        return users.findByMobile(value)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Customer not found"));
    }

    private CheckoutPaymentResponse response(CheckoutSession session) {
        return new CheckoutPaymentResponse(session.getId(), session.getStatus(),
                session.getRazorpayOrderId(), session.getTotalAmount(),
                session.getCurrency(), session.getFinalizedOrderId());
    }

    private record Preparation(String sessionId, Long userId, BigDecimal amount, boolean reuse) {}
}
