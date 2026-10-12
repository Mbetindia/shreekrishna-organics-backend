package com.shreekrishna.organics.payment.service;

import com.shreekrishna.organics.order.entity.Order;
import com.shreekrishna.organics.order.entity.OrderStatus;
import com.shreekrishna.organics.order.entity.PaymentMethod;
import com.shreekrishna.organics.order.entity.PaymentStatus;
import com.shreekrishna.organics.order.entity.StockState;
import com.shreekrishna.organics.order.repository.OrderRepository;
import com.shreekrishna.organics.payment.repository.PaymentRepository;
import com.shreekrishna.organics.user.entity.User;
import com.shreekrishna.organics.user.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;


import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.razorpay.RazorpayException;
import com.shreekrishna.organics.payment.entity.Payment;
import java.util.List;

@Service
public class RazorpayPaymentService {

    private final RazorpayService razorpayService;
    private final RazorpayVerificationService razorpayVerificationService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    public RazorpayPaymentService(
            RazorpayService razorpayService,
            RazorpayVerificationService razorpayVerificationService,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository
    ) {
        this.razorpayService = razorpayService;
        this.razorpayVerificationService = razorpayVerificationService;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
    }

    private Order validateOrder(String email, Long orderId) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Authenticated user not found"
                ));

        System.out.println("PAYMENT DEBUG: orderId=" + orderId + ", userId=" + user.getId());
        Order order = orderRepository
                .findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found"
                ));

        if (order.getPaymentMethod() != PaymentMethod.RAZORPAY) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This order is not a Razorpay order"
            );
        }

        if (order.getStatus() != OrderStatus.PLACED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order is not eligible for payment"
            );
        }

        if (order.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment is not pending"
            );
        }

        if (order.getStockState() != StockState.RESERVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order stock is not reserved"
            );
        }

        return order;
    }
    @Transactional
    public Payment createPayment(String email, Long orderId)
            throws RazorpayException {

        // 1. Customer आणि Order तपासा
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Authenticated user not found"
                ));

        Order order = orderRepository
                .findOwnedOrderForUpdate(orderId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found"
                ));
        if (order.getPaymentMethod() != PaymentMethod.RAZORPAY
                || order.getStatus() != OrderStatus.PLACED
                || order.getPaymentStatus() != PaymentStatus.PENDING
                || order.getStockState() != StockState.RESERVED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order is not eligible for Razorpay payment"
            );
        }

        // 2. आधी Payment तयार झाले आहे का?
        List<Payment> existingPayments =
                paymentRepository.findByOrderId(orderId);

        if (!existingPayments.isEmpty()) {

            Payment existingPayment = existingPayments.get(0);

            if (existingPayments.size() != 1
                    || existingPayment.getStatus()
                    != com.shreekrishna.organics.payment.entity.PaymentStatus.PENDING
                    || existingPayment.getRazorpayOrderId() == null
                    || existingPayment.getRazorpayOrderId().isBlank()
                    || existingPayment.getAmount().compareTo(order.getTotalAmount()) != 0
                    || !order.getCurrency().equals(existingPayment.getCurrency())) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Existing payment requires reconciliation"
                );
            }

            return existingPayment;
        }

        // 3. Database मधील Order Amount वापरा
        if (order.getTotalAmount() == null ||
                order.getTotalAmount().signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid order amount"
            );
        }

        // 4. Razorpay Order तयार करा
        com.razorpay.Order razorpayOrder =
                razorpayService.createRazorpayOrder(
                        order.getTotalAmount(),
                        "order_" + orderId
                );

        // 5. Payment Entity तयार करा
        Payment payment = new Payment();

        payment.setOrderId(orderId);
        payment.setProvider("RAZORPAY");
        payment.setStatus(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PENDING
        );
        payment.setRazorpayOrderId(razorpayOrder.get("id"));
        payment.setAmount(order.getTotalAmount());
        payment.setCurrency("INR");

        // 6. MySQL मध्ये Save करा
        return paymentRepository.save(payment);
    }
    @org.springframework.transaction.annotation.Transactional
    public Payment verifyPayment(
            String email,
            Long orderId,
            com.shreekrishna.organics.payment.dto.RazorpayVerifyRequest request
    ) throws RazorpayException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Customer not found"));

        Order order = orderRepository
                .findOwnedOrderForUpdate(orderId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found"));

        Payment payment = paymentRepository
                .findByRazorpayOrderIdForUpdate(request.razorpayOrderId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment not found"));
        if (!payment.getOrderId().equals(order.getId())
                || order.getPaymentMethod() != PaymentMethod.RAZORPAY) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Payment and order mismatch");
        }

        boolean validSignature = razorpayVerificationService.verifyPayment(
                request.razorpayOrderId(),
                request.razorpayPaymentId(),
                request.razorpaySignature()
        );

        if (!validSignature) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid payment signature");
        }

        com.razorpay.Payment razorpayPayment =
                razorpayService.fetchPayment(request.razorpayPaymentId());

        String status = razorpayPayment.get("status");
        String providerOrderId = razorpayPayment.get("order_id");
        String currency = razorpayPayment.get("currency");
        Number amountInPaise = razorpayPayment.get("amount");

        long expectedPaise = order.getTotalAmount()
                .movePointRight(2)
                .longValueExact();

        if (!"captured".equals(status)
                || !request.razorpayOrderId().equals(providerOrderId)
                || amountInPaise.longValue() != expectedPaise
                || !"INR".equals(currency)
                || payment.getAmount().compareTo(order.getTotalAmount()) != 0
                || !payment.getCurrency().equals(currency)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Payment verification failed");
        }

        if (payment.getStatus()
                == com.shreekrishna.organics.payment.entity.PaymentStatus.PAID) {

            if (!request.razorpayPaymentId()
                    .equals(payment.getRazorpayPaymentId())
                    || order.getPaymentStatus() != PaymentStatus.PAID) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Payment already processed differently");
            }

            return payment;
        }

        if (payment.getStatus()
                != com.shreekrishna.organics.payment.entity.PaymentStatus.PENDING
                || order.getPaymentStatus() != PaymentStatus.PENDING
                || order.getStatus() != OrderStatus.PLACED
                || order.getStockState() != StockState.RESERVED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Order is not eligible for payment");
        }

        payment.setRazorpayPaymentId(request.razorpayPaymentId());
        payment.setStatus(
                com.shreekrishna.organics.payment.entity.PaymentStatus.PAID);

        order.setPaymentStatus(PaymentStatus.PAID);

        orderRepository.save(order);
        return paymentRepository.save(payment);
    }
}