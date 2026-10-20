package com.shreekrishna.organics.checkout.service;

import com.shreekrishna.organics.checkout.dto.CheckoutEligibilityResponse;
import com.shreekrishna.organics.checkout.entity.CheckoutSession;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionRepository;
import com.shreekrishna.organics.user.entity.User;
import com.shreekrishna.organics.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class CheckoutEligibilityService {

    private final CheckoutSessionRepository sessions;
    private final UserRepository users;
    private final boolean onlinePaymentsEnabled;

    public CheckoutEligibilityService(
            CheckoutSessionRepository sessions,
            UserRepository users,
            @Value("${checkout.online-payments-enabled:false}") boolean onlinePaymentsEnabled
    ) {
        this.sessions = sessions;
        this.users = users;
        this.onlinePaymentsEnabled = onlinePaymentsEnabled;
    }

    /**
     * UI advisory only. Payment/order services must enforce eligibility under locks.
     * Supports both email/password and MSG91 mobile OTP identities.
     */
    @Transactional(readOnly = true)
    public CheckoutEligibilityResponse inspect(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authentication required"
            );
        }

        String identifier = username.trim();
        User user;

        if (identifier.contains("@")) {
            user = users.findByEmail(identifier.toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Customer not found"
                    ));
        } else {
            // Match the exact normalized mobile format stored by OTP registration.
            // Do not guess country codes or search by partial phone numbers.
            user = users.findByMobile(identifier)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Customer not found"
                    ));
        }

        Long userId = user.getId();
        List<CheckoutSession> all = sessions.findByUserIdOrderByCreatedAtDesc(userId);

        for (CheckoutSession session : all) {
            String status = session.getStatus();
            boolean providerStarted = notBlank(session.getRazorpayOrderId())
                    || notBlank(session.getRazorpayPaymentId());
            boolean reserved = Boolean.TRUE.equals(session.getStockReserved());
            boolean finalizedOrderExists = session.getFinalizedOrderId() != null;

            if ("FINALIZED".equals(status)
                    && finalizedOrderExists
                    && notBlank(session.getRazorpayOrderId())
                    && notBlank(session.getRazorpayPaymentId())) {
                continue;
            }

            // A COD order already owns the reservation; keep provider reconciliation separate.
            if ("COD_FINALIZED".equals(status) && finalizedOrderExists) {
                continue;
            }

            if ("CREATED".equals(status)
                    && !providerStarted
                    && !reserved
                    && !finalizedOrderExists) {
                continue;
            }

            // A server-guarded abandoned session with released stock is not a blocker.
            if ("ABANDONED".equals(status)
                    && !reserved
                    && !finalizedOrderExists) {
                continue;
            }

            String code = "PAYMENT_PENDING".equals(status)
                    ? "UNRESOLVED_PAYMENT"
                    : "SESSION_REQUIRES_REVIEW";

            return new CheckoutEligibilityResponse(
                    true,
                    false,
                    false,
                    true,
                    session.getId(),
                    code,
                    "Checkout choices may be displayed, "
                            + "but do not create COD or start another charge. "
                            + "Resolve the existing session and "
                            + "reconcile provider payment first."
            );
        }

        return new CheckoutEligibilityResponse(
                true,
                true,
                onlinePaymentsEnabled,
                false,
                null,
                "NO_BLOCKING_CHECKOUT_SESSION",
                onlinePaymentsEnabled
                        ? "No blocking checkout session found. "
                        + "Online payment may be started after server-side validation."
                        : "No blocking checkout session found. "
                        + "COD is available; online payments are disabled."
        );
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
