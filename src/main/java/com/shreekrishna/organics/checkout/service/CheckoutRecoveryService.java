package com.shreekrishna.organics.checkout.service;

import com.shreekrishna.organics.checkout.dto.CheckoutRecoveryResponse;
import com.shreekrishna.organics.checkout.dto.CheckoutReconciliationPreview;
import com.shreekrishna.organics.checkout.entity.CheckoutSession;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionRepository;
import com.shreekrishna.organics.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckoutRecoveryService {
    private final CheckoutSessionRepository sessions;
    private final UserRepository users;
    private final CheckoutReconciliationPreviewService preview;

    public CheckoutRecoveryService(CheckoutSessionRepository sessions,
                                   UserRepository users,
                                   CheckoutReconciliationPreviewService preview) {
        this.sessions = sessions;
        this.users = users;
        this.preview = preview;
    }

    /** Read-only. No database or stock mutation. Does not authorize COD or new charges. */
    public CheckoutRecoveryResponse inspect(String email, String sessionId) {
        Long userId = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"))
                .getId();
        CheckoutSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout session not found"));

        if ("FINALIZED".equals(session.getStatus()) && session.getFinalizedOrderId() != null) {
            return reply(session, "ALREADY_FINALIZED", "ORDER_SUCCESS", false,
                    "The paid order has already been created. Do not pay again.");
        }
        if ("COD_FINALIZED".equals(session.getStatus()) && session.getFinalizedOrderId() != null) {
            return reply(session, "COD_ORDER_CREATED", "ORDER_SUCCESS", false,
                    "This checkout was converted to Cash on Delivery and the order has already been created.");
        }
        if ("ABANDONED".equals(session.getStatus())) {
            return reply(session, "DRAFT_ABANDONED", "CHECKOUT", false,
                    "Draft abandoned. Checkout selection can be shown; fresh order creation still requires backend validation.");
        }
        boolean unstarted = "CREATED".equals(session.getStatus())
                && blank(session.getRazorpayOrderId())
                && blank(session.getRazorpayPaymentId())
                && session.getFinalizedOrderId() == null
                && Boolean.FALSE.equals(session.getStockReserved());
        if (unstarted) {
            return reply(session, "UNSTARTED_DRAFT", "CHECKOUT", true,
                    "Address and payment choices may be displayed. Abandon this unstarted draft before starting a different checkout.");
        }

        CheckoutReconciliationPreview assessment = preview.inspect(email, sessionId);
        String state = assessment.assessment();
        if ("ALREADY_FINALIZED".equals(state)) {
            return reply(session, state, "ORDER_SUCCESS", false,
                    "Order finalized. Do not submit another payment.");
        }
        String message = switch (state) {
            case "CAPTURED_NOT_FINALIZED" ->
                    "Captured payment detected. Do not retry or choose COD. Backend finalization or manual recovery is required.";
            case "AUTHORIZED_NOT_CAPTURED" ->
                    "An authorized payment may still capture. Do not retry or choose COD.";
            case "NO_CAPTURED_PAYMENT_OBSERVED" ->
                    "No captured payment was observed now. You may retry the same online payment, or use the guarded Switch to COD action.";
            default -> "Payment state needs reconciliation. Do not retry or choose COD until the backend permits it.";
        };
        return reply(session, state, "CHECKOUT_WITH_PAYMENT_WARNING", false, message);
    }

    private CheckoutRecoveryResponse reply(CheckoutSession s, String assessment,
                                           String screen, boolean canAbandon, String message) {
        return new CheckoutRecoveryResponse(s.getId(), s.getStatus(), assessment,
                screen, !"ORDER_SUCCESS".equals(screen), canAbandon,
                !"ORDER_SUCCESS".equals(screen), s.getFinalizedOrderId(), message);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
