package com.shreekrishna.organics.payment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import com.shreekrishna.organics.payment.entity.RazorpayWebhookEvent;
import com.shreekrishna.organics.payment.repository.RazorpayWebhookEventRepository;
import tools.jackson.databind.ObjectMapper;
import com.shreekrishna.organics.payment.entity.Payment;
import com.shreekrishna.organics.payment.entity.PaymentAttempt;
import com.shreekrishna.organics.order.entity.Order;
import com.shreekrishna.organics.payment.repository.PaymentRepository;
import com.shreekrishna.organics.payment.repository.PaymentAttemptRepository;
import com.shreekrishna.organics.order.repository.OrderRepository;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import java.util.Optional;

import static org.mockito.Mockito.*;
import com.shreekrishna.organics.payment.entity.PaymentRefund;
import com.shreekrishna.organics.payment.repository.PaymentRefundRepository;

@ExtendWith(MockitoExtension.class)
class RazorpayWebhookProcessingServiceTest {

    @Test
    void shouldRejectMissingWebhookEventId() {

        RazorpayWebhookProcessingService service =
                newService(
                        null, null, null, null, null, null
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.process(
                        "{\"event\":\"payment.captured\"}",
                        ""
                )
        );
    }
    @Test
    void shouldRejectNullWebhookEventId() {

        RazorpayWebhookProcessingService service =
                newService(
                        null, null, null, null, null, null
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.process(
                        "{\"event\":\"payment.captured\"}",
                        null
                )
        );
    }
    @Test
    void shouldIgnoreAlreadyProcessedWebhookEvent() {

        RazorpayWebhookEventRepository eventRepository =
                mock(RazorpayWebhookEventRepository.class);

        RazorpayWebhookEvent savedEvent =
                new RazorpayWebhookEvent();

        savedEvent.setEventId("evt_test_123");
        savedEvent.setEventType("payment.captured");
        savedEvent.setProcessingStatus("PROCESSED");

        when(eventRepository.findByEventIdForUpdate("evt_test_123"))
                .thenReturn(Optional.of(savedEvent));

        RazorpayWebhookProcessingService service =
                newService(
                        new ObjectMapper(),
                        null,
                        null,
                        null,
                        eventRepository,
                        null
                );

        service.process(
                "{\"event\":\"payment.captured\"}",
                "evt_test_123"
        );

        verify(eventRepository, times(1))
                .findByEventIdForUpdate("evt_test_123");

        verify(eventRepository, never())
                .saveAndFlush(any(RazorpayWebhookEvent.class));
    }
    @Test
    void shouldRejectDuplicateEventIdWithDifferentEventType() {

        RazorpayWebhookEventRepository eventRepository =
                mock(RazorpayWebhookEventRepository.class);

        RazorpayWebhookEvent savedEvent =
                new RazorpayWebhookEvent();

        savedEvent.setEventId("evt_test_123");
        savedEvent.setEventType("payment.captured");
        savedEvent.setProcessingStatus("PROCESSED");

        when(eventRepository.findByEventIdForUpdate("evt_test_123"))
                .thenReturn(Optional.of(savedEvent));

        RazorpayWebhookProcessingService service =
                newService(
                        new ObjectMapper(),
                        null,
                        null,
                        null,
                        eventRepository,
                        null
                );

        assertThrows(
                IllegalStateException.class,
                () -> service.process(
                        "{\"event\":\"refund.processed\"}",
                        "evt_test_123"
                )
        );

        verify(eventRepository, never())
                .saveAndFlush(any(RazorpayWebhookEvent.class));
    }
    @Test
    void shouldRejectPaymentCapturedWithoutPaymentDetails() {

        RazorpayWebhookEventRepository eventRepository =
                mock(RazorpayWebhookEventRepository.class);

        when(eventRepository.findByEventIdForUpdate("evt_invalid_payment"))
                .thenReturn(Optional.empty());

        RazorpayWebhookProcessingService service =
                newService(
                        new ObjectMapper(),
                        null,
                        null,
                        null,
                        eventRepository,
                        null
                );

        assertThrows(
                RuntimeException.class,
                () -> service.process(
                        "{\"event\":\"payment.captured\"}",
                        "evt_invalid_payment"
                )
        );
    }
    @Test
    void shouldMarkPaymentAndOrderPaidWhenPaymentCaptured() {

        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        PaymentAttemptRepository attemptRepository =
                mock(PaymentAttemptRepository.class);
        RazorpayWebhookEventRepository eventRepository =
                mock(RazorpayWebhookEventRepository.class);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrderId(2L);
        payment.setRazorpayOrderId("order_test_123");
        payment.setAmount(new BigDecimal("350.00"));
        payment.setCurrency("INR");
        payment.setStatus(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PENDING
        );

        Order order = new Order();
        order.setId(2L);
        order.setTotalAmount(new BigDecimal("350.00"));
        order.setCurrency("INR");
        order.setPaymentMethod(
                com.shreekrishna.organics.order.entity.PaymentMethod.RAZORPAY
        );
        order.setPaymentStatus(
                com.shreekrishna.organics.order.entity.PaymentStatus.PENDING
        );
        order.setStatus(
                com.shreekrishna.organics.order.entity.OrderStatus.PLACED
        );
        order.setStockState(
                com.shreekrishna.organics.order.entity.StockState.RESERVED
        );

        when(eventRepository.findByEventIdForUpdate("evt_capture_123"))
                .thenReturn(Optional.empty());

        when(paymentRepository.findByRazorpayOrderId("order_test_123"))
                .thenReturn(Optional.of(payment));

        when(orderRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByRazorpayOrderIdForUpdate("order_test_123"))
                .thenReturn(Optional.of(payment));

        when(attemptRepository.findByRazorpayPaymentId("pay_test_123"))
                .thenReturn(Optional.empty());

        RazorpayWebhookProcessingService service =
                newService(
                        new ObjectMapper(),
                        paymentRepository,
                        orderRepository,
                        attemptRepository,
                        eventRepository,
                        null
                );

        String payload = """
            {
              "event": "payment.captured",
              "payload": {
                "payment": {
                  "entity": {
                    "id": "pay_test_123",
                    "order_id": "order_test_123",
                    "status": "captured",
                    "currency": "INR",
                    "amount": 35000
                  }
                }
              }
            }
            """;

        service.process(payload, "evt_capture_123");

        assertEquals(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID,
                payment.getStatus()
        );

        assertEquals(
                com.shreekrishna.organics.order.entity.PaymentStatus.PAID,
                order.getPaymentStatus()
        );

        assertEquals("pay_test_123", payment.getRazorpayPaymentId());

        verify(attemptRepository).save(
                argThat(attempt ->
                        "CAPTURED".equals(attempt.getStatus())
                                && attempt.getPaymentId().equals(1L)
                                && "pay_test_123".equals(attempt.getRazorpayPaymentId())
                )
        );

        verify(paymentRepository).save(payment);
        verify(orderRepository).save(order);
    }
    @Test
    void shouldRejectCapturedPaymentWithWrongAmount() {

        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        PaymentAttemptRepository attemptRepository =
                mock(PaymentAttemptRepository.class);
        RazorpayWebhookEventRepository eventRepository =
                mock(RazorpayWebhookEventRepository.class);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrderId(2L);
        payment.setRazorpayOrderId("order_test_123");
        payment.setAmount(new BigDecimal("350.00"));
        payment.setCurrency("INR");
        payment.setStatus(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PENDING
        );

        Order order = new Order();
        order.setId(2L);
        order.setTotalAmount(new BigDecimal("350.00"));
        order.setCurrency("INR");
        order.setPaymentMethod(
                com.shreekrishna.organics.order.entity.PaymentMethod.RAZORPAY
        );
        order.setPaymentStatus(
                com.shreekrishna.organics.order.entity.PaymentStatus.PENDING
        );

        when(eventRepository.findByEventIdForUpdate("evt_wrong_amount"))
                .thenReturn(Optional.empty());

        when(paymentRepository.findByRazorpayOrderId("order_test_123"))
                .thenReturn(Optional.of(payment));

        when(orderRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByRazorpayOrderIdForUpdate("order_test_123"))
                .thenReturn(Optional.of(payment));

        RazorpayWebhookProcessingService service =
                newService(
                        new ObjectMapper(),
                        paymentRepository,
                        orderRepository,
                        attemptRepository,
                        eventRepository,
                        null
                );

        String payload = """
            {
              "event": "payment.captured",
              "payload": {
                "payment": {
                  "entity": {
                    "id": "pay_wrong_amount",
                    "order_id": "order_test_123",
                    "status": "captured",
                    "currency": "INR",
                    "amount": 30000
                  }
                }
              }
            }
            """;

        assertThrows(
                IllegalStateException.class,
                () -> service.process(payload, "evt_wrong_amount")
        );

        assertEquals(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PENDING,
                payment.getStatus()
        );

        assertEquals(
                com.shreekrishna.organics.order.entity.PaymentStatus.PENDING,
                order.getPaymentStatus()
        );

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(orderRepository, never()).save(any(Order.class));
        verify(attemptRepository, never()).save(any(PaymentAttempt.class));
    }
    @Test
    void shouldHandleDuplicateCapturedPaymentWithoutSavingAgain() {

        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        PaymentAttemptRepository attemptRepository =
                mock(PaymentAttemptRepository.class);
        RazorpayWebhookEventRepository eventRepository =
                mock(RazorpayWebhookEventRepository.class);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrderId(2L);
        payment.setRazorpayOrderId("order_test_123");
        payment.setRazorpayPaymentId("pay_test_123");
        payment.setAmount(new BigDecimal("350.00"));
        payment.setCurrency("INR");
        payment.setStatus(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID
        );

        Order order = new Order();
        order.setId(2L);
        order.setTotalAmount(new BigDecimal("350.00"));
        order.setCurrency("INR");
        order.setPaymentMethod(
                com.shreekrishna.organics.order.entity.PaymentMethod.RAZORPAY
        );
        order.setPaymentStatus(
                com.shreekrishna.organics.order.entity.PaymentStatus.PAID
        );

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPaymentId(1L);
        attempt.setRazorpayPaymentId("pay_test_123");
        attempt.setStatus("CAPTURED");

        when(eventRepository.findByEventIdForUpdate("evt_duplicate_capture"))
                .thenReturn(Optional.empty());

        when(paymentRepository.findByRazorpayOrderId("order_test_123"))
                .thenReturn(Optional.of(payment));

        when(orderRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByRazorpayOrderIdForUpdate("order_test_123"))
                .thenReturn(Optional.of(payment));

        when(attemptRepository.findByRazorpayPaymentId("pay_test_123"))
                .thenReturn(Optional.of(attempt));

        RazorpayWebhookProcessingService service =
                newService(
                        new ObjectMapper(),
                        paymentRepository,
                        orderRepository,
                        attemptRepository,
                        eventRepository,
                        null
                );

        String payload = """
            {
              "event": "payment.captured",
              "payload": {
                "payment": {
                  "entity": {
                    "id": "pay_test_123",
                    "order_id": "order_test_123",
                    "status": "captured",
                    "currency": "INR",
                    "amount": 35000
                  }
                }
              }
            }
            """;

        service.process(payload, "evt_duplicate_capture");

        assertEquals(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID,
                payment.getStatus()
        );

        assertEquals(
                com.shreekrishna.organics.order.entity.PaymentStatus.PAID,
                order.getPaymentStatus()
        );

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(orderRepository, never()).save(any(Order.class));
        verify(attemptRepository, never()).save(any(PaymentAttempt.class));
    }
    @Test
    void shouldRecordFailedAttemptWithoutFailingOrder() {

        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        PaymentAttemptRepository attemptRepository =
                mock(PaymentAttemptRepository.class);
        RazorpayWebhookEventRepository eventRepository =
                mock(RazorpayWebhookEventRepository.class);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrderId(2L);
        payment.setRazorpayOrderId("order_test_123");
        payment.setAmount(new BigDecimal("350.00"));
        payment.setCurrency("INR");
        payment.setStatus(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PENDING
        );

        Order order = new Order();
        order.setId(2L);
        order.setTotalAmount(new BigDecimal("350.00"));
        order.setCurrency("INR");
        order.setPaymentMethod(
                com.shreekrishna.organics.order.entity.PaymentMethod.RAZORPAY
        );
        order.setPaymentStatus(
                com.shreekrishna.organics.order.entity.PaymentStatus.PENDING
        );

        when(eventRepository.findByEventIdForUpdate("evt_failed_123"))
                .thenReturn(Optional.empty());

        when(paymentRepository.findByRazorpayOrderId("order_test_123"))
                .thenReturn(Optional.of(payment));

        when(orderRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByRazorpayOrderIdForUpdate("order_test_123"))
                .thenReturn(Optional.of(payment));

        when(attemptRepository.findByRazorpayPaymentId("pay_failed_123"))
                .thenReturn(Optional.empty());

        RazorpayWebhookProcessingService service =
                newService(
                        new ObjectMapper(),
                        paymentRepository,
                        orderRepository,
                        attemptRepository,
                        eventRepository,
                        null
                );

        String payload = """
            {
              "event": "payment.failed",
              "payload": {
                "payment": {
                  "entity": {
                    "id": "pay_failed_123",
                    "order_id": "order_test_123",
                    "status": "failed",
                    "currency": "INR",
                    "amount": 35000,
                    "error_code": "BAD_REQUEST_ERROR",
                    "error_description": "Payment failed"
                  }
                }
              }
            }
            """;

        service.process(payload, "evt_failed_123");

        assertEquals(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PENDING,
                payment.getStatus()
        );

        assertEquals(
                com.shreekrishna.organics.order.entity.PaymentStatus.PENDING,
                order.getPaymentStatus()
        );

        verify(attemptRepository).save(
                argThat(attempt ->
                        "FAILED".equals(attempt.getStatus())
                                && Long.valueOf(1L).equals(attempt.getPaymentId())
                                && "pay_failed_123".equals(attempt.getRazorpayPaymentId())
                                && "BAD_REQUEST_ERROR".equals(attempt.getFailureCode())
                )
        );

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(orderRepository, never()).save(any(Order.class));
    }
    @Test
    void shouldNotSaveDuplicateFailedPaymentAttempt() {

        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        PaymentAttemptRepository attemptRepository =
                mock(PaymentAttemptRepository.class);
        RazorpayWebhookEventRepository eventRepository =
                mock(RazorpayWebhookEventRepository.class);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrderId(2L);
        payment.setRazorpayOrderId("order_test_123");
        payment.setAmount(new BigDecimal("350.00"));
        payment.setCurrency("INR");
        payment.setStatus(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PENDING
        );

        Order order = new Order();
        order.setId(2L);
        order.setTotalAmount(new BigDecimal("350.00"));
        order.setCurrency("INR");
        order.setPaymentMethod(
                com.shreekrishna.organics.order.entity.PaymentMethod.RAZORPAY
        );
        order.setPaymentStatus(
                com.shreekrishna.organics.order.entity.PaymentStatus.PENDING
        );

        PaymentAttempt existingAttempt = new PaymentAttempt();
        existingAttempt.setPaymentId(1L);
        existingAttempt.setRazorpayPaymentId("pay_failed_123");
        existingAttempt.setStatus("FAILED");

        when(eventRepository.findByEventIdForUpdate("evt_failed_duplicate"))
                .thenReturn(Optional.empty());

        when(paymentRepository.findByRazorpayOrderId("order_test_123"))
                .thenReturn(Optional.of(payment));

        when(orderRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByRazorpayOrderIdForUpdate("order_test_123"))
                .thenReturn(Optional.of(payment));

        when(attemptRepository.findByRazorpayPaymentId("pay_failed_123"))
                .thenReturn(Optional.of(existingAttempt));

        RazorpayWebhookProcessingService service =
                newService(
                        new ObjectMapper(),
                        paymentRepository,
                        orderRepository,
                        attemptRepository,
                        eventRepository,
                        null
                );

        String payload = """
            {
              "event": "payment.failed",
              "payload": {
                "payment": {
                  "entity": {
                    "id": "pay_failed_123",
                    "order_id": "order_test_123",
                    "status": "failed",
                    "currency": "INR",
                    "amount": 35000
                  }
                }
              }
            }
            """;

        service.process(payload, "evt_failed_duplicate");

        verify(attemptRepository, never())
                .save(any(PaymentAttempt.class));

        verify(paymentRepository, never())
                .save(any(Payment.class));

        verify(orderRepository, never())
                .save(any(Order.class));

        assertEquals(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PENDING,
                payment.getStatus()
        );

        assertEquals(
                com.shreekrishna.organics.order.entity.PaymentStatus.PENDING,
                order.getPaymentStatus()
        );
    }
    @Test
    void shouldSavePartialRefundAndKeepPaymentPaid() {

        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        PaymentRefundRepository refundRepository =
                mock(PaymentRefundRepository.class);
        RazorpayWebhookEventRepository eventRepository =
                mock(RazorpayWebhookEventRepository.class);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrderId(2L);
        payment.setRazorpayPaymentId("pay_test_123");
        payment.setAmount(new BigDecimal("350.00"));
        payment.setCurrency("INR");
        payment.setStatus(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID
        );

        Order order = new Order();
        order.setId(2L);
        order.setPaymentMethod(
                com.shreekrishna.organics.order.entity.PaymentMethod.RAZORPAY
        );
        order.setPaymentStatus(
                com.shreekrishna.organics.order.entity.PaymentStatus.PAID
        );

        when(eventRepository.findByEventIdForUpdate("evt_refund_100"))
                .thenReturn(Optional.empty());

        when(paymentRepository.findByRazorpayPaymentId("pay_test_123"))
                .thenReturn(Optional.of(payment));

        when(orderRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByRazorpayPaymentIdForUpdate("pay_test_123"))
                .thenReturn(Optional.of(payment));

        when(refundRepository.findByRazorpayRefundId("rfnd_test_100"))
                .thenReturn(Optional.empty());

        when(refundRepository.findByPaymentId(1L))
                .thenReturn(java.util.List.of());

        RazorpayWebhookProcessingService service =
                newService(
                        new ObjectMapper(),
                        paymentRepository,
                        orderRepository,
                        null,
                        eventRepository,
                        refundRepository
                );

        String payload = """
            {
              "event": "refund.processed",
              "payload": {
                "refund": {
                  "entity": {
                    "id": "rfnd_test_100",
                    "payment_id": "pay_test_123",
                    "status": "processed",
                    "currency": "INR",
                    "amount": 10000
                  }
                }
              }
            }
            """;

        service.process(payload, "evt_refund_100");

        verify(refundRepository).saveAndFlush(
                argThat(refund ->
                        "rfnd_test_100".equals(refund.getRazorpayRefundId())
                                && refund.getAmount().compareTo(
                                new BigDecimal("100.00")) == 0
                                && "PROCESSED".equals(refund.getStatus())
                )
        );

        assertEquals(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID,
                payment.getStatus()
        );

        assertEquals(
                com.shreekrishna.organics.order.entity.PaymentStatus.PAID,
                order.getPaymentStatus()
        );

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(orderRepository, never()).save(any(Order.class));
    }
    @Test
    void shouldMarkPaymentAndOrderRefundedAfterFullRefund() {

        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        PaymentRefundRepository refundRepository =
                mock(PaymentRefundRepository.class);
        RazorpayWebhookEventRepository eventRepository =
                mock(RazorpayWebhookEventRepository.class);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrderId(2L);
        payment.setRazorpayPaymentId("pay_full_123");
        payment.setAmount(new BigDecimal("350.00"));
        payment.setCurrency("INR");
        payment.setStatus(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID
        );

        Order order = new Order();
        order.setId(2L);
        order.setPaymentMethod(
                com.shreekrishna.organics.order.entity.PaymentMethod.RAZORPAY
        );
        order.setPaymentStatus(
                com.shreekrishna.organics.order.entity.PaymentStatus.PAID
        );

        when(eventRepository.findByEventIdForUpdate("evt_full_refund"))
                .thenReturn(Optional.empty());

        when(paymentRepository.findByRazorpayPaymentId("pay_full_123"))
                .thenReturn(Optional.of(payment));

        when(orderRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByRazorpayPaymentIdForUpdate("pay_full_123"))
                .thenReturn(Optional.of(payment));

        when(refundRepository.findByRazorpayRefundId("rfnd_full_350"))
                .thenReturn(Optional.empty());

        when(refundRepository.findByPaymentId(1L))
                .thenReturn(java.util.List.of());

        RazorpayWebhookProcessingService service =
                newService(
                        new ObjectMapper(),
                        paymentRepository,
                        orderRepository,
                        null,
                        eventRepository,
                        refundRepository
                );

        String payload = """
            {
              "event": "refund.processed",
              "payload": {
                "refund": {
                  "entity": {
                    "id": "rfnd_full_350",
                    "payment_id": "pay_full_123",
                    "status": "processed",
                    "currency": "INR",
                    "amount": 35000
                  }
                }
              }
            }
            """;

        service.process(payload, "evt_full_refund");

        verify(refundRepository).saveAndFlush(
                argThat(refund ->
                        "rfnd_full_350".equals(refund.getRazorpayRefundId())
                                && refund.getAmount().compareTo(
                                new BigDecimal("350.00")) == 0
                                && "PROCESSED".equals(refund.getStatus())
                )
        );

        assertEquals(
                com.shreekrishna.organics.payment.entity.PaymentStatus.REFUNDED,
                payment.getStatus()
        );

        assertEquals(
                com.shreekrishna.organics.order.entity.PaymentStatus.REFUNDED,
                order.getPaymentStatus()
        );

        verify(paymentRepository).save(payment);
        verify(orderRepository).save(order);
    }
    @Test
    void shouldMarkRefundedWhenMultiplePartialRefundsReachFullAmount() {

        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        PaymentRefundRepository refundRepository =
                mock(PaymentRefundRepository.class);
        RazorpayWebhookEventRepository eventRepository =
                mock(RazorpayWebhookEventRepository.class);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrderId(2L);
        payment.setRazorpayPaymentId("pay_multi_123");
        payment.setAmount(new BigDecimal("350.00"));
        payment.setCurrency("INR");
        payment.setStatus(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID
        );

        Order order = new Order();
        order.setId(2L);
        order.setPaymentMethod(
                com.shreekrishna.organics.order.entity.PaymentMethod.RAZORPAY
        );
        order.setPaymentStatus(
                com.shreekrishna.organics.order.entity.PaymentStatus.PAID
        );

        PaymentRefund firstRefund = new PaymentRefund();
        firstRefund.setPaymentId(1L);
        firstRefund.setAmount(new BigDecimal("100.00"));
        firstRefund.setStatus("PROCESSED");

        PaymentRefund secondRefund = new PaymentRefund();
        secondRefund.setPaymentId(1L);
        secondRefund.setAmount(new BigDecimal("150.00"));
        secondRefund.setStatus("PROCESSED");

        when(eventRepository.findByEventIdForUpdate("evt_last_refund"))
                .thenReturn(Optional.empty());

        when(paymentRepository.findByRazorpayPaymentId("pay_multi_123"))
                .thenReturn(Optional.of(payment));

        when(orderRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByRazorpayPaymentIdForUpdate("pay_multi_123"))
                .thenReturn(Optional.of(payment));

        when(refundRepository.findByRazorpayRefundId("rfnd_last_100"))
                .thenReturn(Optional.empty());

        when(refundRepository.findByPaymentId(1L))
                .thenReturn(java.util.List.of(firstRefund, secondRefund));

        RazorpayWebhookProcessingService service =
                newService(
                        new ObjectMapper(),
                        paymentRepository,
                        orderRepository,
                        null,
                        eventRepository,
                        refundRepository
                );

        String payload = """
            {
              "event": "refund.processed",
              "payload": {
                "refund": {
                  "entity": {
                    "id": "rfnd_last_100",
                    "payment_id": "pay_multi_123",
                    "status": "processed",
                    "currency": "INR",
                    "amount": 10000
                  }
                }
              }
            }
            """;

        service.process(payload, "evt_last_refund");

        verify(refundRepository).saveAndFlush(
                argThat(refund ->
                        "rfnd_last_100".equals(refund.getRazorpayRefundId())
                                && refund.getAmount().compareTo(
                                new BigDecimal("100.00")) == 0
                )
        );

        assertEquals(
                com.shreekrishna.organics.payment.entity.PaymentStatus.REFUNDED,
                payment.getStatus()
        );

        assertEquals(
                com.shreekrishna.organics.order.entity.PaymentStatus.REFUNDED,
                order.getPaymentStatus()
        );

        verify(paymentRepository).save(payment);
        verify(orderRepository).save(order);
    }
    @Test
    void shouldRejectRefundExceedingOriginalPayment() {

        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        PaymentRefundRepository refundRepository =
                mock(PaymentRefundRepository.class);
        RazorpayWebhookEventRepository eventRepository =
                mock(RazorpayWebhookEventRepository.class);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrderId(2L);
        payment.setRazorpayPaymentId("pay_over_123");
        payment.setAmount(new BigDecimal("350.00"));
        payment.setCurrency("INR");
        payment.setStatus(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID
        );

        Order order = new Order();
        order.setId(2L);
        order.setPaymentMethod(
                com.shreekrishna.organics.order.entity.PaymentMethod.RAZORPAY
        );
        order.setPaymentStatus(
                com.shreekrishna.organics.order.entity.PaymentStatus.PAID
        );

        PaymentRefund previousRefund = new PaymentRefund();
        previousRefund.setPaymentId(1L);
        previousRefund.setAmount(new BigDecimal("250.00"));
        previousRefund.setStatus("PROCESSED");

        when(eventRepository.findByEventIdForUpdate("evt_over_refund"))
                .thenReturn(Optional.empty());

        when(paymentRepository.findByRazorpayPaymentId("pay_over_123"))
                .thenReturn(Optional.of(payment));

        when(orderRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByRazorpayPaymentIdForUpdate("pay_over_123"))
                .thenReturn(Optional.of(payment));

        when(refundRepository.findByRazorpayRefundId("rfnd_over_150"))
                .thenReturn(Optional.empty());

        when(refundRepository.findByPaymentId(1L))
                .thenReturn(java.util.List.of(previousRefund));

        RazorpayWebhookProcessingService service =
                newService(
                        new ObjectMapper(),
                        paymentRepository,
                        orderRepository,
                        null,
                        eventRepository,
                        refundRepository
                );

        String payload = """
            {
              "event": "refund.processed",
              "payload": {
                "refund": {
                  "entity": {
                    "id": "rfnd_over_150",
                    "payment_id": "pay_over_123",
                    "status": "processed",
                    "currency": "INR",
                    "amount": 15000
                  }
                }
              }
            }
            """;

        assertThrows(
                IllegalStateException.class,
                () -> service.process(payload, "evt_over_refund")
        );

        assertEquals(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID,
                payment.getStatus()
        );

        assertEquals(
                com.shreekrishna.organics.order.entity.PaymentStatus.PAID,
                order.getPaymentStatus()
        );

        verify(refundRepository, never())
                .saveAndFlush(any(PaymentRefund.class));

        verify(paymentRepository, never())
                .save(any(Payment.class));

        verify(orderRepository, never())
                .save(any(Order.class));
    }
    @Test
    void shouldNotSaveDuplicateProcessedRefund() {

        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        PaymentRefundRepository refundRepository =
                mock(PaymentRefundRepository.class);
        RazorpayWebhookEventRepository eventRepository =
                mock(RazorpayWebhookEventRepository.class);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrderId(2L);
        payment.setRazorpayPaymentId("pay_duplicate_123");
        payment.setAmount(new BigDecimal("350.00"));
        payment.setCurrency("INR");
        payment.setStatus(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID
        );

        Order order = new Order();
        order.setId(2L);
        order.setPaymentMethod(
                com.shreekrishna.organics.order.entity.PaymentMethod.RAZORPAY
        );
        order.setPaymentStatus(
                com.shreekrishna.organics.order.entity.PaymentStatus.PAID
        );

        PaymentRefund existingRefund = new PaymentRefund();
        existingRefund.setPaymentId(1L);
        existingRefund.setRazorpayRefundId("rfnd_duplicate_100");
        existingRefund.setRazorpayPaymentId("pay_duplicate_123");
        existingRefund.setAmount(new BigDecimal("100.00"));
        existingRefund.setCurrency("INR");
        existingRefund.setStatus("PROCESSED");

        when(eventRepository.findByEventIdForUpdate("evt_duplicate_refund"))
                .thenReturn(Optional.empty());

        when(paymentRepository.findByRazorpayPaymentId("pay_duplicate_123"))
                .thenReturn(Optional.of(payment));

        when(orderRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByRazorpayPaymentIdForUpdate("pay_duplicate_123"))
                .thenReturn(Optional.of(payment));

        when(refundRepository.findByRazorpayRefundId("rfnd_duplicate_100"))
                .thenReturn(Optional.of(existingRefund));

        RazorpayWebhookProcessingService service =
                newService(
                        new ObjectMapper(),
                        paymentRepository,
                        orderRepository,
                        null,
                        eventRepository,
                        refundRepository
                );

        String payload = """
            {
              "event": "refund.processed",
              "payload": {
                "refund": {
                  "entity": {
                    "id": "rfnd_duplicate_100",
                    "payment_id": "pay_duplicate_123",
                    "status": "processed",
                    "currency": "INR",
                    "amount": 10000
                  }
                }
              }
            }
            """;

        service.process(payload, "evt_duplicate_refund");

        verify(refundRepository, never())
                .saveAndFlush(any(PaymentRefund.class));

        verify(paymentRepository, never())
                .save(any(Payment.class));

        verify(orderRepository, never())
                .save(any(Order.class));

        assertEquals(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID,
                payment.getStatus()
        );
    }
    @Test
    void shouldRejectDuplicateRefundIdWithDifferentAmount() {

        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        PaymentRefundRepository refundRepository =
                mock(PaymentRefundRepository.class);
        RazorpayWebhookEventRepository eventRepository =
                mock(RazorpayWebhookEventRepository.class);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrderId(2L);
        payment.setRazorpayPaymentId("pay_duplicate_123");
        payment.setAmount(new BigDecimal("350.00"));
        payment.setCurrency("INR");
        payment.setStatus(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID
        );

        Order order = new Order();
        order.setId(2L);
        order.setPaymentMethod(
                com.shreekrishna.organics.order.entity.PaymentMethod.RAZORPAY
        );
        order.setPaymentStatus(
                com.shreekrishna.organics.order.entity.PaymentStatus.PAID
        );

        PaymentRefund existingRefund = new PaymentRefund();
        existingRefund.setPaymentId(1L);
        existingRefund.setRazorpayRefundId("rfnd_duplicate_100");
        existingRefund.setRazorpayPaymentId("pay_duplicate_123");
        existingRefund.setAmount(new BigDecimal("100.00"));
        existingRefund.setCurrency("INR");
        existingRefund.setStatus("PROCESSED");

        when(eventRepository.findByEventIdForUpdate("evt_changed_amount"))
                .thenReturn(Optional.empty());

        when(paymentRepository.findByRazorpayPaymentId("pay_duplicate_123"))
                .thenReturn(Optional.of(payment));

        when(orderRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByRazorpayPaymentIdForUpdate("pay_duplicate_123"))
                .thenReturn(Optional.of(payment));

        when(refundRepository.findByRazorpayRefundId("rfnd_duplicate_100"))
                .thenReturn(Optional.of(existingRefund));

        RazorpayWebhookProcessingService service =
                newService(
                        new ObjectMapper(),
                        paymentRepository,
                        orderRepository,
                        null,
                        eventRepository,
                        refundRepository
                );

        String payload = """
            {
              "event": "refund.processed",
              "payload": {
                "refund": {
                  "entity": {
                    "id": "rfnd_duplicate_100",
                    "payment_id": "pay_duplicate_123",
                    "status": "processed",
                    "currency": "INR",
                    "amount": 15000
                  }
                }
              }
            }
            """;

        assertThrows(
                IllegalStateException.class,
                () -> service.process(payload, "evt_changed_amount")
        );

        verify(refundRepository, never())
                .saveAndFlush(any(PaymentRefund.class));

        verify(paymentRepository, never())
                .save(any(Payment.class));

        verify(orderRepository, never())
                .save(any(Order.class));
    }

    @Test
    void shouldIgnoreCapturedWebhookAfterFullRefund() {

        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        PaymentAttemptRepository attemptRepository =
                mock(PaymentAttemptRepository.class);
        RazorpayWebhookEventRepository eventRepository =
                mock(RazorpayWebhookEventRepository.class);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrderId(2L);
        payment.setRazorpayOrderId("order_test_123");
        payment.setRazorpayPaymentId("pay_test_123");
        payment.setAmount(new BigDecimal("350.00"));
        payment.setCurrency("INR");
        payment.setStatus(
                com.shreekrishna.organics.payment.entity.PaymentStatus.REFUNDED
        );

        Order order = new Order();
        order.setId(2L);
        order.setTotalAmount(new BigDecimal("350.00"));
        order.setCurrency("INR");
        order.setPaymentMethod(
                com.shreekrishna.organics.order.entity.PaymentMethod.RAZORPAY
        );
        order.setPaymentStatus(
                com.shreekrishna.organics.order.entity.PaymentStatus.REFUNDED
        );

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPaymentId(1L);
        attempt.setRazorpayPaymentId("pay_test_123");
        attempt.setStatus("CAPTURED");

        when(eventRepository.findByEventIdForUpdate("evt_capture_after_refund"))
                .thenReturn(Optional.empty());

        when(paymentRepository.findByRazorpayOrderId("order_test_123"))
                .thenReturn(Optional.of(payment));

        when(orderRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByRazorpayOrderIdForUpdate("order_test_123"))
                .thenReturn(Optional.of(payment));

        when(attemptRepository.findByRazorpayPaymentId("pay_test_123"))
                .thenReturn(Optional.of(attempt));

        RazorpayWebhookProcessingService service =
                newService(
                        new ObjectMapper(),
                        paymentRepository,
                        orderRepository,
                        attemptRepository,
                        eventRepository,
                        null
                );

        String payload = """
            {
              "event": "payment.captured",
              "payload": {
                "payment": {
                  "entity": {
                    "id": "pay_test_123",
                    "order_id": "order_test_123",
                    "status": "captured",
                    "currency": "INR",
                    "amount": 35000
                  }
                }
              }
            }
            """;

        service.process(payload, "evt_capture_after_refund");

        assertEquals(
                com.shreekrishna.organics.payment.entity.PaymentStatus.REFUNDED,
                payment.getStatus()
        );

        assertEquals(
                com.shreekrishna.organics.order.entity.PaymentStatus.REFUNDED,
                order.getPaymentStatus()
        );

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(orderRepository, never()).save(any(Order.class));
        verify(attemptRepository, never()).save(any(PaymentAttempt.class));
    }

    private RazorpayWebhookProcessingService newService(
            ObjectMapper objectMapper,
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            PaymentAttemptRepository attemptRepository,
            RazorpayWebhookEventRepository eventRepository,
            PaymentRefundRepository refundRepository
    ) {
        return new RazorpayWebhookProcessingService(
                objectMapper,
                paymentRepository,
                orderRepository,
                attemptRepository,
                eventRepository,
                refundRepository,
                mock(com.shreekrishna.organics.checkout.repository.CheckoutSessionRepository.class),
                mock(com.shreekrishna.organics.checkout.service.CheckoutPaymentFinalizationService.class)
        );
    }

}
