
package com.shreekrishna.organics.payment.integration;

import com.shreekrishna.organics.order.entity.Order;
import com.shreekrishna.organics.order.entity.OrderStatus;
import com.shreekrishna.organics.order.entity.PaymentMethod;
import com.shreekrishna.organics.order.entity.StockState;
import com.shreekrishna.organics.order.repository.OrderRepository;
import com.shreekrishna.organics.payment.entity.Payment;
import com.shreekrishna.organics.payment.repository.PaymentRepository;
import com.shreekrishna.organics.payment.service.RazorpayWebhookProcessingService;
import com.shreekrishna.organics.user.entity.User;
import com.shreekrishna.organics.user.repository.UserRepository;
import com.shreekrishna.organics.payment.entity.PaymentAttempt;
import com.shreekrishna.organics.payment.repository.PaymentAttemptRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.shreekrishna.organics.payment.entity.PaymentRefund;
import com.shreekrishna.organics.payment.repository.PaymentRefundRepository;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RazorpayWebhookIntegrationTest {

    @Autowired
    private RazorpayWebhookProcessingService processingService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;
    @Autowired
    private PaymentRefundRepository paymentRefundRepository;

    @Test
    void shouldMarkPaymentAndOrderPaidAfterCapturedWebhook() {

        String uniqueId = UUID.randomUUID()
                .toString()
                .replace("-", "");

        // Create a test user in the separate test database
        User testUser = new User();
        testUser.setFirstName("Payment");
        testUser.setLastName("Test");
        testUser.setEmail("payment-" + uniqueId + "@example.com");
        testUser.setPassword("integration-test-not-for-login");

        testUser = userRepository.saveAndFlush(testUser);

// Create order for the saved test user
        Order order = new Order();
        order.setOrderNumber("TEST-" + uniqueId);
        order.setUserId(testUser.getId());
        order.setStatus(OrderStatus.PLACED);
        order.setPaymentMethod(PaymentMethod.RAZORPAY);
        order.setPaymentStatus(
                com.shreekrishna.organics.order.entity.PaymentStatus.PENDING
        );
        order.setStockState(StockState.RESERVED);
        order.setSubtotal(new BigDecimal("350.00"));
        order.setTotalAmount(new BigDecimal("350.00"));

        order.setShippingFullName("Integration Test");
        order.setShippingMobile("9999999999");
        order.setShippingAddressLine1("Test Address");
        order.setShippingCity("Pune");
        order.setShippingState("Maharashtra");
        order.setShippingPostalCode("411001");
        order.setShippingCountry("India");

        order = orderRepository.saveAndFlush(order);

        String razorpayOrderId = "order_" + uniqueId;
        String razorpayPaymentId = "pay_" + uniqueId;

        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setRazorpayOrderId(razorpayOrderId);
        payment.setAmount(new BigDecimal("350.00"));
        payment.setCurrency("INR");

        payment = paymentRepository.saveAndFlush(payment);

        String payload = """
                {
                  "event": "payment.captured",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "%s",
                        "order_id": "%s",
                        "status": "captured",
                        "amount": 35000,
                        "currency": "INR"
                      }
                    }
                  }
                }
                """.formatted(
                razorpayPaymentId,
                razorpayOrderId
        );

        String eventId = "evt_" + uniqueId;

        processingService.process(payload, eventId);

        Payment updatedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        Order updatedOrder = orderRepository
                .findById(order.getId())
                .orElseThrow();

        assertEquals(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID,
                updatedPayment.getStatus()
        );

        assertEquals(
                com.shreekrishna.organics.order.entity.PaymentStatus.PAID,
                updatedOrder.getPaymentStatus()
        );

        assertEquals(
                razorpayPaymentId,
                updatedPayment.getRazorpayPaymentId()
        );

        // Duplicate delivery should not change payment status.
        processingService.process(payload, eventId);

        Payment afterDuplicate = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertEquals(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID,
                afterDuplicate.getStatus()
        );
    }

    @Test
    void shouldSaveFailedPaymentAttemptAndKeepOrderPending() {

        String uniqueId = UUID.randomUUID()
                .toString()
                .replace("-", "");

        // Create test customer
        User testUser = new User();
        testUser.setFirstName("Failed");
        testUser.setLastName("PaymentTest");
        testUser.setEmail("failed-" + uniqueId + "@example.com");
        testUser.setPassword("integration-test-not-for-login");

        testUser = userRepository.saveAndFlush(testUser);

        // Create test order
        Order order = new Order();
        order.setOrderNumber("FAIL-" + uniqueId);
        order.setUserId(testUser.getId());
        order.setStatus(OrderStatus.PLACED);
        order.setPaymentMethod(PaymentMethod.RAZORPAY);
        order.setPaymentStatus(
                com.shreekrishna.organics.order.entity.PaymentStatus.PENDING
        );
        order.setStockState(StockState.RESERVED);
        order.setSubtotal(new BigDecimal("350.00"));
        order.setTotalAmount(new BigDecimal("350.00"));

        order.setShippingFullName("Failed Payment Test");
        order.setShippingMobile("9999999999");
        order.setShippingAddressLine1("Test Address");
        order.setShippingCity("Pune");
        order.setShippingState("Maharashtra");
        order.setShippingPostalCode("411001");
        order.setShippingCountry("India");

        order = orderRepository.saveAndFlush(order);

        String razorpayOrderId = "order_" + uniqueId;
        String razorpayPaymentId = "pay_" + uniqueId;

        // Create pending payment
        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setRazorpayOrderId(razorpayOrderId);
        payment.setAmount(new BigDecimal("350.00"));
        payment.setCurrency("INR");

        payment = paymentRepository.saveAndFlush(payment);

        // Simulated Razorpay payment.failed webhook
        String payload = """
            {
              "event": "payment.failed",
              "payload": {
                "payment": {
                  "entity": {
                    "id": "%s",
                    "order_id": "%s",
                    "status": "failed",
                    "amount": 35000,
                    "currency": "INR",
                    "error_code": "BAD_REQUEST_ERROR",
                    "error_description": "Payment failed during test"
                  }
                }
              }
            }
            """.formatted(
                razorpayPaymentId,
                razorpayOrderId
        );

        String eventId = "evt_failed_" + uniqueId;

        processingService.process(payload, eventId);

        // Verify failed attempt was saved
        PaymentAttempt attempt = paymentAttemptRepository
                .findByRazorpayPaymentId(razorpayPaymentId)
                .orElseThrow();

        assertEquals("FAILED", attempt.getStatus());
        assertEquals(payment.getId(), attempt.getPaymentId());
        assertEquals("BAD_REQUEST_ERROR", attempt.getFailureCode());
        assertEquals(
                "Payment failed during test",
                attempt.getFailureDescription()
        );

        // Main payment should remain pending for retry
        Payment updatedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        Order updatedOrder = orderRepository
                .findById(order.getId())
                .orElseThrow();

        assertEquals(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PENDING,
                updatedPayment.getStatus()
        );

        assertEquals(
                com.shreekrishna.organics.order.entity.PaymentStatus.PENDING,
                updatedOrder.getPaymentStatus()
        );

        // Same webhook event must not create another attempt
        processingService.process(payload, eventId);

        assertEquals(
                1,
                paymentAttemptRepository.findByPaymentId(payment.getId()).size()
        );
    }

    @Test
    void shouldHandlePartialAndFullRefund() {

        String uniqueId = UUID.randomUUID()
                .toString()
                .replace("-", "");

        // 1. Create test customer
        User testUser = new User();
        testUser.setFirstName("Refund");
        testUser.setLastName("Test");
        testUser.setEmail("refund-" + uniqueId + "@example.com");
        testUser.setPassword("integration-test-not-for-login");

        testUser = userRepository.saveAndFlush(testUser);

        // 2. Create paid Razorpay order
        Order order = new Order();
        order.setOrderNumber("REF-" + uniqueId);
        order.setUserId(testUser.getId());
        order.setStatus(OrderStatus.PLACED);
        order.setPaymentMethod(PaymentMethod.RAZORPAY);
        order.setPaymentStatus(
                com.shreekrishna.organics.order.entity.PaymentStatus.PAID
        );
        order.setStockState(StockState.RESERVED);
        order.setSubtotal(new BigDecimal("350.00"));
        order.setTotalAmount(new BigDecimal("350.00"));

        order.setShippingFullName("Refund Integration Test");
        order.setShippingMobile("9999999999");
        order.setShippingAddressLine1("Test Address");
        order.setShippingCity("Pune");
        order.setShippingState("Maharashtra");
        order.setShippingPostalCode("411001");
        order.setShippingCountry("India");

        order = orderRepository.saveAndFlush(order);

        String razorpayPaymentId = "pay_" + uniqueId;

        // 3. Create paid payment
        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setRazorpayOrderId("order_" + uniqueId);
        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setStatus(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID
        );
        payment.setAmount(new BigDecimal("350.00"));
        payment.setCurrency("INR");

        payment = paymentRepository.saveAndFlush(payment);

        // 4. Partial refund: Rs. 100
        String partialRefundId = "rfnd_partial_" + uniqueId;

        String partialPayload = """
            {
              "event": "refund.processed",
              "payload": {
                "refund": {
                  "entity": {
                    "id": "%s",
                    "payment_id": "%s",
                    "status": "processed",
                    "amount": 10000,
                    "currency": "INR"
                  }
                }
              }
            }
            """.formatted(
                partialRefundId,
                razorpayPaymentId
        );

        processingService.process(
                partialPayload,
                "evt_partial_" + uniqueId
        );

        PaymentRefund partialRefund = paymentRefundRepository
                .findByRazorpayRefundId(partialRefundId)
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("100.00")
                        .compareTo(partialRefund.getAmount())
        );
        assertEquals("PROCESSED", partialRefund.getStatus());

        assertEquals(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID,
                paymentRepository.findById(payment.getId())
                        .orElseThrow().getStatus()
        );

        assertEquals(
                com.shreekrishna.organics.order.entity.PaymentStatus.PAID,
                orderRepository.findById(order.getId())
                        .orElseThrow().getPaymentStatus()
        );

        // 5. Remaining refund: Rs. 250
        String finalRefundId = "rfnd_final_" + uniqueId;

        String finalPayload = """
            {
              "event": "refund.processed",
              "payload": {
                "refund": {
                  "entity": {
                    "id": "%s",
                    "payment_id": "%s",
                    "status": "processed",
                    "amount": 25000,
                    "currency": "INR"
                  }
                }
              }
            }
            """.formatted(
                finalRefundId,
                razorpayPaymentId
        );

        String finalEventId = "evt_final_" + uniqueId;

        processingService.process(finalPayload, finalEventId);

        // 6. Entire amount refunded
        assertEquals(
                com.shreekrishna.organics.payment.entity.PaymentStatus.REFUNDED,
                paymentRepository.findById(payment.getId())
                        .orElseThrow().getStatus()
        );

        assertEquals(
                com.shreekrishna.organics.order.entity.PaymentStatus.REFUNDED,
                orderRepository.findById(order.getId())
                        .orElseThrow().getPaymentStatus()
        );

        assertEquals(
                2,
                paymentRefundRepository.findByPaymentId(payment.getId()).size()
        );

        // 7. Duplicate webhook must not create another refund
        processingService.process(finalPayload, finalEventId);

        assertEquals(
                2,
                paymentRefundRepository.findByPaymentId(payment.getId()).size()
        );
    }


}
