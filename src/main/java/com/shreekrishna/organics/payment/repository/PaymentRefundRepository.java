package com.shreekrishna.organics.payment.repository;

import com.shreekrishna.organics.payment.entity.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRefundRepository
        extends JpaRepository<PaymentRefund, Long> {

    Optional<PaymentRefund> findByRazorpayRefundId(
            String razorpayRefundId
    );

    List<PaymentRefund> findByPaymentId(Long paymentId);

    List<PaymentRefund> findByRazorpayPaymentId(
            String razorpayPaymentId
    );
}