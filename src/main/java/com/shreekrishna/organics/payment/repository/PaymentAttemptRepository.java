package com.shreekrishna.organics.payment.repository;

import com.shreekrishna.organics.payment.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepository
        extends JpaRepository<PaymentAttempt, Long> {

    Optional<PaymentAttempt> findByRazorpayPaymentId(
            String razorpayPaymentId
    );

    List<PaymentAttempt> findByPaymentId(Long paymentId);
}