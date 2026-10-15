package com.shreekrishna.organics.payment.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import com.shreekrishna.organics.checkout.repository.CheckoutSessionRepository;
import com.shreekrishna.organics.checkout.service.CheckoutPaymentFinalizationService;
import com.shreekrishna.organics.payment.repository.PaymentRepository;
import com.shreekrishna.organics.order.repository.OrderRepository;
import com.shreekrishna.organics.payment.repository.PaymentAttemptRepository;
import com.shreekrishna.organics.payment.repository.RazorpayWebhookEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import com.shreekrishna.organics.payment.repository.PaymentRefundRepository;

@Service
public class RazorpayWebhookProcessingService {

    private final ObjectMapper objectMapper;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final CheckoutPaymentFinalizationService checkoutFinalizer;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final RazorpayWebhookEventRepository webhookEventRepository;
    private final PaymentRefundRepository paymentRefundRepository;

    public RazorpayWebhookProcessingService(
            ObjectMapper objectMapper,
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            RazorpayWebhookEventRepository webhookEventRepository,
            PaymentRefundRepository paymentRefundRepository,
            CheckoutSessionRepository checkoutSessionRepository,
            CheckoutPaymentFinalizationService checkoutFinalizer) {

        this.objectMapper = objectMapper;
        this.checkoutSessionRepository = checkoutSessionRepository;
        this.checkoutFinalizer = checkoutFinalizer;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.paymentRefundRepository = paymentRefundRepository;
    }
    @org.springframework.transaction.annotation.Transactional
    public void process(String payload, String eventId) {
        if (eventId == null || eventId.isBlank()
                || eventId.length() > 150) {

            throw new IllegalArgumentException(
                    "Invalid Razorpay webhook event ID"
            );
        }



        try {
            JsonNode root = objectMapper.readTree(payload);

            String event = root.path("event").asText("");
            if (event.isBlank() || event.length() > 100) {
                throw new IllegalArgumentException(
                        "Invalid Razorpay webhook event type"
                );
            }
            var existingEvent = webhookEventRepository.findByEventIdForUpdate(eventId);
        if (existingEvent.isPresent()) {

            var savedEvent = existingEvent.get();

            if (!event.equals(savedEvent.getEventType())) {
                throw new IllegalStateException(
                        "Webhook event ID belongs to a different event type"
                );
            }

            if ("PROCESSED".equals(savedEvent.getProcessingStatus())) {
                return;
            }
            throw new IllegalStateException(
                    "Webhook event is not fully processed"
            );
        }

            var webhookEvent =
                    new com.shreekrishna.organics.payment.entity.RazorpayWebhookEvent();

            webhookEvent.setEventId(eventId);
            webhookEvent.setEventType(event);
            webhookEvent.setProcessingStatus("RECEIVED");

            try {
                webhookEventRepository.saveAndFlush(webhookEvent);

            } catch (DataIntegrityViolationException e) {

                throw new DuplicateWebhookEventException(
                        "Webhook event insert conflict",
                        e
                );
            }

            switch (event) {

                case "payment.captured" ->
                        handlePaymentCaptured(root);

                case "payment.failed" ->
                        handlePaymentFailed(root);

                case "refund.processed" ->
                        handleRefundProcessed(root);

                default -> {
                    // Ignore unsupported events.
                }
            }
            webhookEvent.setProcessingStatus("PROCESSED");
            webhookEvent.setProcessedAt(
                    java.time.LocalDateTime.now()
            );

            webhookEventRepository.save(webhookEvent);

        } catch (tools.jackson.core.JacksonException e){
            throw new IllegalArgumentException(
                    "Invalid webhook JSON", e);
        }
    }

    private void handlePaymentCaptured(JsonNode root) {

        JsonNode payment = root.path("payload")
                .path("payment")
                .path("entity");

        String razorpayPaymentId = payment.path("id").asText("");
        String razorpayOrderId = payment.path("order_id").asText("");
        String status = payment.path("status").asText("");
        String currency = payment.path("currency").asText("");

        JsonNode amountNode = payment.path("amount");

        if (razorpayPaymentId.isBlank()
                || razorpayOrderId.isBlank()
                || !"captured".equals(status)
                || !"INR".equals(currency)
                || !amountNode.isIntegralNumber()
                || amountNode.longValue() <= 0) {

            throw new IllegalArgumentException(
                    "Invalid payment.captured webhook payload"
            );
        }

        long amountInPaise = amountNode.longValue();
        // New checkout flow: a provider order can belong to a checkout session
        // without an existing customer order or row in the payments table.
        // The webhook signature MUST already have been checked by the webhook controller.
        if (checkoutSessionRepository.findByRazorpayOrderId(razorpayOrderId).isPresent()) {
            checkoutFinalizer.finalizeCapturedWebhook(
                    razorpayOrderId, razorpayPaymentId, amountInPaise, currency);
            return; // Existing order-first payment handler remains unchanged.
        }
        // Find payment record using Razorpay Order ID
        com.shreekrishna.organics.payment.entity.Payment existingPayment =
                paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Payment record not found"
                        ));

// Lock Order first
        com.shreekrishna.organics.order.entity.Order order =
                orderRepository.findByIdForUpdate(existingPayment.getOrderId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Order record not found"
                        ));

// Lock Payment second
        com.shreekrishna.organics.payment.entity.Payment lockedPayment =
                paymentRepository.findByRazorpayOrderIdForUpdate(razorpayOrderId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Payment record not found"
                        ));
        // Verify payment belongs to the locked order
        if (!lockedPayment.getOrderId().equals(order.getId())
                || !razorpayOrderId.equals(lockedPayment.getRazorpayOrderId())
                || order.getPaymentMethod()
                != com.shreekrishna.organics.order.entity.PaymentMethod.RAZORPAY) {

            throw new IllegalStateException(
                    "Payment and order mismatch"
            );
        }

// Verify amount and currency
        long expectedPaise = order.getTotalAmount()
                .movePointRight(2)
                .longValueExact();

        if (amountInPaise != expectedPaise
                || lockedPayment.getAmount().compareTo(order.getTotalAmount()) != 0
                || !currency.equals(order.getCurrency())
                || !currency.equals(lockedPayment.getCurrency())) {

            throw new IllegalStateException(
                    "Payment amount or currency mismatch"
            );
        }

// Handle duplicate captured webhook

        if (lockedPayment.getStatus()
                == com.shreekrishna.organics.payment.entity.PaymentStatus.PAID
                || lockedPayment.getStatus()
                == com.shreekrishna.organics.payment.entity.PaymentStatus.REFUNDED) {

            boolean alreadyRefunded =
                    lockedPayment.getStatus()
                            == com.shreekrishna.organics.payment.entity.PaymentStatus.REFUNDED;

            if (!razorpayPaymentId.equals(lockedPayment.getRazorpayPaymentId())
                    || order.getPaymentStatus()
                    != (alreadyRefunded
                    ? com.shreekrishna.organics.order.entity.PaymentStatus.REFUNDED
                    : com.shreekrishna.organics.order.entity.PaymentStatus.PAID)) {

                throw new IllegalStateException(
                        "Payment already processed differently"
                );
            }

            var existingAttempt = paymentAttemptRepository
                    .findByRazorpayPaymentId(razorpayPaymentId);

            if (existingAttempt.isPresent()) {

                var attempt = existingAttempt.get();

                if (!attempt.getPaymentId().equals(lockedPayment.getId())
                        || !"CAPTURED".equals(attempt.getStatus())) {

                    throw new IllegalStateException(
                            "Conflicting captured payment attempt"
                    );
                }

            } else {

                // Do not recreate a missing captured attempt after a full refund.
                if (alreadyRefunded) {
                    throw new IllegalStateException(
                            "Captured attempt missing for refunded payment"
                    );
                }

                var attempt =
                        new com.shreekrishna.organics.payment.entity.PaymentAttempt();

                attempt.setPaymentId(lockedPayment.getId());
                attempt.setRazorpayPaymentId(razorpayPaymentId);
                attempt.setStatus("CAPTURED");
                attempt.setAmount(lockedPayment.getAmount());
                attempt.setCurrency(lockedPayment.getCurrency());

                paymentAttemptRepository.save(attempt);
            }

            return;
        }





// Check order eligibility
        if (lockedPayment.getStatus()
                != com.shreekrishna.organics.payment.entity.PaymentStatus.PENDING
                || order.getPaymentStatus()
                != com.shreekrishna.organics.order.entity.PaymentStatus.PENDING
                || order.getStatus()
                != com.shreekrishna.organics.order.entity.OrderStatus.PLACED
                || order.getStockState()
                != com.shreekrishna.organics.order.entity.StockState.RESERVED) {

            throw new IllegalStateException(
                    "Order is not eligible for payment"
            );
        }

        // Prevent conflicting Razorpay payment IDs
        if (lockedPayment.getRazorpayPaymentId() != null
                && !lockedPayment.getRazorpayPaymentId()
                .equals(razorpayPaymentId)) {

            throw new IllegalStateException(
                    "Different Razorpay payment already recorded"
            );
        }
        var existingAttempt = paymentAttemptRepository
                .findByRazorpayPaymentId(razorpayPaymentId);

        if (existingAttempt.isPresent()) {

            var attempt = existingAttempt.get();

            if (!attempt.getPaymentId().equals(lockedPayment.getId())
                    || !"CAPTURED".equals(attempt.getStatus())) {

                throw new IllegalStateException(
                        "Conflicting payment attempt record"
                );
            }

        } else {

            var attempt =
                    new com.shreekrishna.organics.payment.entity.PaymentAttempt();

            attempt.setPaymentId(lockedPayment.getId());
            attempt.setRazorpayPaymentId(razorpayPaymentId);
            attempt.setStatus("CAPTURED");
            attempt.setAmount(lockedPayment.getAmount());
            attempt.setCurrency(lockedPayment.getCurrency());

            paymentAttemptRepository.save(attempt);
        }
// Mark payment as paid
        lockedPayment.setRazorpayPaymentId(razorpayPaymentId);

        lockedPayment.setStatus(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID
        );

// Mark order payment as paid
        order.setPaymentStatus(
                com.shreekrishna.organics.order.entity.PaymentStatus.PAID
        );

// Save both records in the same transaction
        paymentRepository.save(lockedPayment);
        orderRepository.save(order);
    }
    private void handlePaymentFailed(JsonNode root) {

        JsonNode payment = root.path("payload")
                .path("payment")
                .path("entity");

        String razorpayPaymentId = payment.path("id").asText("");
        String razorpayOrderId = payment.path("order_id").asText("");
        String status = payment.path("status").asText("");
        String currency = payment.path("currency").asText("");

        JsonNode amountNode = payment.path("amount");

        if (razorpayPaymentId.isBlank()
                || razorpayOrderId.isBlank()
                || !"failed".equals(status)
                || !"INR".equals(currency)
                || !amountNode.isIntegralNumber()
                || amountNode.longValue() <= 0) {

            throw new IllegalArgumentException(
                    "Invalid payment.failed webhook payload"
            );
        }

        // A failed attempt on a checkout-session payment is NOT proof that
        // another attempt cannot capture. Leave the session recoverable.
        // Future work: record failed attempts in checkout-specific audit table.
        if (checkoutSessionRepository.findByRazorpayOrderId(razorpayOrderId).isPresent()) {
            return;
        }
        // Find the parent payment record
        com.shreekrishna.organics.payment.entity.Payment existingPayment =
                paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Payment record not found"
                        ));

// Lock Order first
        com.shreekrishna.organics.order.entity.Order order =
                orderRepository.findByIdForUpdate(existingPayment.getOrderId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Order record not found"
                        ));

// Lock Payment second
        com.shreekrishna.organics.payment.entity.Payment lockedPayment =
                paymentRepository.findByRazorpayOrderIdForUpdate(razorpayOrderId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Payment record not found"
                        ));

// Verify payment belongs to this order
        if (!lockedPayment.getOrderId().equals(order.getId())
                || !razorpayOrderId.equals(lockedPayment.getRazorpayOrderId())
                || order.getPaymentMethod()
                != com.shreekrishna.organics.order.entity.PaymentMethod.RAZORPAY) {

            throw new IllegalStateException(
                    "Payment and order mismatch"
            );
        }

// Validate amount and currency
        long expectedPaise = order.getTotalAmount()
                .movePointRight(2)
                .longValueExact();

        if (amountNode.longValue() != expectedPaise
                || lockedPayment.getAmount().compareTo(order.getTotalAmount()) != 0
                || !currency.equals(order.getCurrency())
                || !currency.equals(lockedPayment.getCurrency())) {

            throw new IllegalStateException(
                    "Payment amount or currency mismatch"
            );
        }

// Check whether this attempt was already recorded
        var existingAttempt = paymentAttemptRepository
                .findByRazorpayPaymentId(razorpayPaymentId);

        if (existingAttempt.isPresent()) {

            var attempt = existingAttempt.get();

            if (!attempt.getPaymentId().equals(lockedPayment.getId())
                    || !"FAILED".equals(attempt.getStatus())) {

                throw new IllegalStateException(
                        "Conflicting payment attempt record"
                );
            }

            // Duplicate webhook: already recorded
            return;
        }

// Record failed attempt without failing the parent order
        var attempt =
                new com.shreekrishna.organics.payment.entity.PaymentAttempt();

        attempt.setPaymentId(lockedPayment.getId());
        attempt.setRazorpayPaymentId(razorpayPaymentId);
        attempt.setStatus("FAILED");
        attempt.setAmount(lockedPayment.getAmount());
        attempt.setCurrency(currency);

        String failureCode = payment.path("error_code").asText("");
        String failureDescription =
                payment.path("error_description").asText("");

        attempt.setFailureCode(
                failureCode.isBlank() ? null :
                        failureCode.substring(0, Math.min(failureCode.length(), 100))
        );

        attempt.setFailureDescription(
                failureDescription.isBlank() ? null :
                        failureDescription.substring(
                                0, Math.min(failureDescription.length(), 500))
        );

        paymentAttemptRepository.save(attempt);
    }

    private void handleRefundProcessed(JsonNode root) {

        JsonNode refund = root.path("payload")
                .path("refund")
                .path("entity");

        String razorpayRefundId =
                refund.path("id").asText("");

        String razorpayPaymentId =
                refund.path("payment_id").asText("");

        String status =
                refund.path("status").asText("");

        String currency =
                refund.path("currency").asText("");

        JsonNode amountNode = refund.path("amount");

        if (razorpayRefundId.isBlank()
                || razorpayPaymentId.isBlank()
                || !"processed".equals(status)
                || !"INR".equals(currency)
                || !amountNode.isIntegralNumber()
                || amountNode.longValue() <= 0) {

            throw new IllegalArgumentException(
                    "Invalid refund.processed webhook payload"
            );
        }

        // Find original payment using Razorpay Payment ID
        var originalPayment = paymentRepository
                .findByRazorpayPaymentId(razorpayPaymentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Original payment not found for refund"
                ));
// Lock Order first
        var lockedOrder = orderRepository
                .findByIdForUpdate(originalPayment.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order not found for refund"
                ));

// Lock original Payment second
        originalPayment = paymentRepository
                .findByRazorpayPaymentIdForUpdate(razorpayPaymentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Original payment not found for refund"
                ));

// Validate payment and order association
        if (!originalPayment.getOrderId().equals(lockedOrder.getId())
                || lockedOrder.getPaymentMethod()
                != com.shreekrishna.organics.order.entity.PaymentMethod.RAZORPAY
                || !razorpayPaymentId.equals(
                originalPayment.getRazorpayPaymentId())) {

            throw new IllegalStateException(
                    "Refund payment and order mismatch"
            );
        }
// Only successful payments can be refunded
        if (originalPayment.getStatus()
                != com.shreekrishna.organics.payment.entity.PaymentStatus.PAID
                && originalPayment.getStatus()
                != com.shreekrishna.organics.payment.entity.PaymentStatus.REFUNDED) {

            throw new IllegalStateException(
                    "Refund received for non-paid payment"
            );
        }

// Refund amount must not exceed original payment amount
        long originalAmountPaise = originalPayment.getAmount()
                .movePointRight(2)
                .longValueExact();

        if (amountNode.longValue() > originalAmountPaise
                || !currency.equals(originalPayment.getCurrency())) {

            throw new IllegalStateException(
                    "Refund amount or currency mismatch"
            );
        }

// Check whether this refund is already recorded
        var existingRefund = paymentRefundRepository
                .findByRazorpayRefundId(razorpayRefundId);

        // Check consistency of an already fully refunded payment
        if (originalPayment.getStatus()
                == com.shreekrishna.organics.payment.entity.PaymentStatus.REFUNDED
                && lockedOrder.getPaymentStatus()
                != com.shreekrishna.organics.order.entity.PaymentStatus.REFUNDED) {

            throw new IllegalStateException(
                    "Refunded payment and order status mismatch"
            );
        }
        if (existingRefund.isPresent()) {
            var savedRefund = existingRefund.get();

            if (!savedRefund.getPaymentId().equals(originalPayment.getId())
                    || !razorpayPaymentId.equals(savedRefund.getRazorpayPaymentId())
                    || savedRefund.getAmount().movePointRight(2)
                    .longValueExact() != amountNode.longValue()
                    || !currency.equals(savedRefund.getCurrency())
                    || !"PROCESSED".equals(savedRefund.getStatus())) {

                throw new IllegalStateException(
                        "Conflicting refund record"
                );
            }

            return;
        }

// Calculate total refunds already processed
        java.math.BigDecimal totalRefunded = paymentRefundRepository
                .findByPaymentId(originalPayment.getId())
                .stream()
                .filter(savedRefund -> "PROCESSED".equals(savedRefund.getStatus()))
                .map(savedRefund -> savedRefund.getAmount())
                .reduce(
                        java.math.BigDecimal.ZERO,
                        java.math.BigDecimal::add
                );

// Convert Razorpay refund amount from paise to rupees
        java.math.BigDecimal newRefundAmount =
                java.math.BigDecimal.valueOf(
                        amountNode.longValue(), 2
                );

// Calculate cumulative refund amount
        java.math.BigDecimal cumulativeRefund =
                totalRefunded.add(newRefundAmount);

// Prevent refund amount exceeding original payment
        if (cumulativeRefund.compareTo(originalPayment.getAmount()) > 0) {

            throw new IllegalStateException(
                    "Cumulative refund exceeds original payment amount"
            );
        }

// Create refund record
        var paymentRefund =
                new com.shreekrishna.organics.payment.entity.PaymentRefund();

        paymentRefund.setPaymentId(originalPayment.getId());
        paymentRefund.setRazorpayRefundId(razorpayRefundId);
        paymentRefund.setRazorpayPaymentId(razorpayPaymentId);
        paymentRefund.setAmount(newRefundAmount);
        paymentRefund.setCurrency(currency);
        paymentRefund.setStatus("PROCESSED");
        paymentRefundRepository.saveAndFlush(paymentRefund);

// Save refund in database
        // Update payment status only when fully refunded
        if (cumulativeRefund.compareTo(originalPayment.getAmount()) == 0) {

            originalPayment.setStatus(
                    com.shreekrishna.organics.payment.entity.PaymentStatus.REFUNDED
            );

            lockedOrder.setPaymentStatus(
                    com.shreekrishna.organics.order.entity.PaymentStatus.REFUNDED
            );

            paymentRepository.save(originalPayment);
            orderRepository.save(lockedOrder);
        }
    }
}
