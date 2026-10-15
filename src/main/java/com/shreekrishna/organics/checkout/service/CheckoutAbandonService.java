package com.shreekrishna.organics.checkout.service;

import com.shreekrishna.organics.checkout.dto.CheckoutAbandonResponse;
import com.shreekrishna.organics.checkout.entity.CheckoutSession;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionRepository;
import com.shreekrishna.organics.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckoutAbandonService {
    private final CheckoutSessionRepository sessions;
    private final UserRepository users;

    public CheckoutAbandonService(CheckoutSessionRepository sessions, UserRepository users) {
        this.sessions = sessions;
        this.users = users;
    }

    @Transactional
    public CheckoutAbandonResponse abandonUnstartedDraft(String email, String sessionId) {
        Long userId = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"))
                .getId();
        // Use the same session lock as payment start and payment finalization.
        CheckoutSession session = sessions.findOwnedForUpdate(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout session not found"));

        // Idempotent only if this session has never been sent to Razorpay.
        if ("ABANDONED".equals(session.getStatus()) && safeToAbandon(session)) {
            return new CheckoutAbandonResponse(session.getId(), "ABANDONED", "Draft was already abandoned");
        }
        if (!"CREATED".equals(session.getStatus()) || !safeToAbandon(session)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Checkout may have an initiated or captured payment; do not switch to COD until reconciled");
        }
        session.setStatus("ABANDONED");
        sessions.saveAndFlush(session);
        return new CheckoutAbandonResponse(session.getId(), "ABANDONED",
                "Unstarted checkout draft abandoned; cart and stock unchanged");
    }

    private boolean safeToAbandon(CheckoutSession session) {
        return (session.getRazorpayOrderId() == null || session.getRazorpayOrderId().isBlank())
                && (session.getRazorpayPaymentId() == null || session.getRazorpayPaymentId().isBlank())
                && session.getFinalizedOrderId() == null
                && Boolean.FALSE.equals(session.getStockReserved());
    }
}
