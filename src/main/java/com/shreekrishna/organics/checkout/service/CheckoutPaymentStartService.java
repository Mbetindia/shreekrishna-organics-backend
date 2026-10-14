package com.shreekrishna.organics.checkout.service;

import com.razorpay.RazorpayException;
import com.shreekrishna.organics.checkout.dto.CheckoutPaymentResponse;
import com.shreekrishna.organics.checkout.entity.CheckoutSession;
import com.shreekrishna.organics.checkout.entity.CheckoutSessionItem;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionItemRepository;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionRepository;
import com.shreekrishna.organics.inventory.service.InventoryService;
import com.shreekrishna.organics.payment.service.RazorpayService;
import com.shreekrishna.organics.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Isolated checkout-session Razorpay initializer.
 * Disabled by default until captured-payment finalization AND webhook routing are installed.
 * No customer order is created, and cart items are not removed here.
 */
@Service
public class CheckoutPaymentStartService {
    private final CheckoutSessionRepository sessions;
    private final CheckoutSessionItemRepository items;
    private final UserRepository users;
    private final InventoryService inventory;
    private final RazorpayService razorpay;
    private final boolean enabled;

    public CheckoutPaymentStartService(
            CheckoutSessionRepository sessions,
            CheckoutSessionItemRepository items,
            UserRepository users,
            InventoryService inventory,
            RazorpayService razorpay,
            @Value("${checkout.online-payments-enabled:false}") boolean enabled) {
        this.sessions = sessions;
        this.items = items;
        this.users = users;
        this.inventory = inventory;
        this.razorpay = razorpay;
        this.enabled = enabled;
    }

    @Transactional
    public CheckoutPaymentResponse start(String email, String sessionId) throws RazorpayException {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "New checkout-session payments are disabled until finalization and webhooks are installed");
        }
        Long userId = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"))
                .getId();
        CheckoutSession session = sessions.findOwnedForUpdate(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout session not found"));

        if ("PAYMENT_PENDING".equals(session.getStatus())
                && session.getRazorpayOrderId() != null
                && !session.getRazorpayOrderId().isBlank()) {
            return response(session); // retries reuse the provider order
        }
        if (!"CREATED".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout session not eligible");
        }
        if (!session.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout draft expired; create a new draft");
        }
        if (session.getTotalAmount() == null || session.getTotalAmount().signum() <= 0
                || !"INR".equals(session.getCurrency())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid checkout currency or amount");
        }
        List<CheckoutSessionItem> snapshots = items.findBySession_IdOrderByIdAsc(sessionId);
        if (snapshots.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout has no items");
        }
        // Lock variants in stable order. Transaction rollback undoes reservations if provider API fails.
        snapshots.stream().sorted(Comparator.comparing(CheckoutSessionItem::getVariantId))
                .forEach(i -> inventory.reserveStock(i.getVariantId(), i.getQuantity()));
        session.setStockReserved(true);
        // Razorpay receipts must fit provider limits; UUID alone is 36 characters.
        com.razorpay.Order providerOrder = razorpay.createRazorpayOrder(session.getTotalAmount(), session.getId());
        session.setRazorpayOrderId(providerOrder.get("id"));
        session.setStatus("PAYMENT_PENDING");
        sessions.saveAndFlush(session);
        return response(session);
    }

    @Transactional(readOnly = true)
    public CheckoutPaymentResponse status(String email, String sessionId) {
        Long userId = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"))
                .getId();
        CheckoutSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout session not found"));
        return response(session);
    }

    private CheckoutPaymentResponse response(CheckoutSession session) {
        return new CheckoutPaymentResponse(session.getId(), session.getStatus(),
                session.getRazorpayOrderId(), session.getTotalAmount(),
                session.getCurrency(), session.getFinalizedOrderId());
    }
}
