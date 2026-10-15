package com.shreekrishna.organics.checkout.service;

import com.shreekrishna.organics.checkout.dto.CheckoutEligibilityResponse;
import com.shreekrishna.organics.checkout.entity.CheckoutSession;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionRepository;
import com.shreekrishna.organics.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CheckoutEligibilityService {
    private final CheckoutSessionRepository sessions;
    private final UserRepository users;
    private final boolean onlinePaymentsEnabled;

    public CheckoutEligibilityService(
            CheckoutSessionRepository sessions,
            UserRepository users,
            @Value("${checkout.online-payments-enabled:false}") boolean onlinePaymentsEnabled) {
        this.sessions = sessions;
        this.users = users;
        this.onlinePaymentsEnabled = onlinePaymentsEnabled;
    }

    /** UI advisory only. Actual payment/order services must enforce eligibility under locks. */
    @Transactional(readOnly = true)
    public CheckoutEligibilityResponse inspect(String email) {
        Long userId = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"))
                .getId();
        List<CheckoutSession> all = sessions.findByUserIdOrderByCreatedAtDesc(userId);

        for (CheckoutSession s : all) {
            String status = s.getStatus();
            boolean providerStarted = notBlank(s.getRazorpayOrderId())
                    || notBlank(s.getRazorpayPaymentId());
            boolean reserved = Boolean.TRUE.equals(s.getStockReserved());
            boolean finalizedOrderExists = s.getFinalizedOrderId() != null;

            if ("FINALIZED".equals(status)
                    && finalizedOrderExists
                    && notBlank(s.getRazorpayOrderId())
                    && notBlank(s.getRazorpayPaymentId())) {
                continue;
            }

            // COD_FINALIZED uses the same checkout reservation, now transferred to the COD order.
            // A linked Razorpay order may still exist for late-capture reconciliation, but this
            // checkout is no longer an unresolved payment blocker.
            if ("COD_FINALIZED".equals(status) && finalizedOrderExists) {
                continue;
            }

            if ("CREATED".equals(status)
                    && !providerStarted && !reserved && !finalizedOrderExists) {
                continue;
            }

            // A server-guarded cancellation may keep the historical Razorpay order ID
            // for audit/reconciliation. Once the checkout reservation is released and
            // no order was finalized, it must no longer block a fresh checkout.
            if ("ABANDONED".equals(status)
                    && !reserved && !finalizedOrderExists) {
                continue;
            }

            String code = "PAYMENT_PENDING".equals(status)
                    ? "UNRESOLVED_PAYMENT" : "SESSION_REQUIRES_REVIEW";
            return new CheckoutEligibilityResponse(true, false, false, true,
                    s.getId(), code,
                    "Checkout choices may be displayed, but do not create COD or start another charge. "
                            + "Resolve the existing session and reconcile provider payment first.");
        }

        return new CheckoutEligibilityResponse(true, true, onlinePaymentsEnabled, false,
                null, "NO_BLOCKING_CHECKOUT_SESSION",
                onlinePaymentsEnabled
                        ? "No blocking checkout session found. Online payment may be started after server-side validation."
                        : "No blocking checkout session found. COD is available; online payments are disabled.");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
