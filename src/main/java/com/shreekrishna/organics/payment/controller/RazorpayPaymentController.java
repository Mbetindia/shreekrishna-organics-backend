package com.shreekrishna.organics.payment.controller;

import com.razorpay.RazorpayException;
import com.shreekrishna.organics.payment.entity.Payment;
import com.shreekrishna.organics.payment.service.RazorpayPaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.shreekrishna.organics.payment.dto.RazorpayVerifyRequest;
import jakarta.validation.Valid;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class RazorpayPaymentController {

    private final RazorpayPaymentService paymentService;

    public RazorpayPaymentController(
            RazorpayPaymentService paymentService
    ) {
        this.paymentService = paymentService;
    }

    @PostMapping("/razorpay/create/{orderId}")
    public ResponseEntity<Map<String, Object>> createPayment(
            @PathVariable Long orderId,
            Authentication authentication
    ) throws RazorpayException {

        Payment payment = paymentService.createPayment(
                authentication.getName(),
                orderId
        );

        return ResponseEntity.ok(Map.of(
                "paymentId", payment.getId(),
                "orderId", payment.getOrderId(),
                "razorpayOrderId", payment.getRazorpayOrderId(),
                "amount", payment.getAmount(),
                "currency", payment.getCurrency(),
                "status", payment.getStatus().name()
        ));
    }
    @PostMapping("/razorpay/verify/{orderId}")
    public ResponseEntity<Map<String, Object>> verifyPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody RazorpayVerifyRequest request,
            Authentication authentication
    ) throws RazorpayException {

        Payment payment = paymentService.verifyPayment(
                authentication.getName(),
                orderId,
                request
        );

        return ResponseEntity.ok(Map.of(
                "paymentId", payment.getId(),
                "orderId", payment.getOrderId(),
                "razorpayOrderId", payment.getRazorpayOrderId(),
                "razorpayPaymentId", payment.getRazorpayPaymentId(),
                "amount", payment.getAmount(),
                "currency", payment.getCurrency(),
                "status", payment.getStatus().name()
        ));
    }
}