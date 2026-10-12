package com.shreekrishna.organics.payment.repository;

import com.shreekrishna.organics.payment.entity.Payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface PaymentRepository
        extends JpaRepository<Payment, Long> {

    Optional<Payment> findByRazorpayOrderId(
            String razorpayOrderId
    );

    Optional<Payment> findByRazorpayPaymentId(
            String razorpayPaymentId
    );

    List<Payment> findByOrderId(Long orderId);

    // Lock payment row during webhook processing
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM Payment p
            WHERE p.razorpayOrderId = :razorpayOrderId
            """)
    Optional<Payment> findByRazorpayOrderIdForUpdate(
            @Param("razorpayOrderId") String razorpayOrderId
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p FROM Payment p
        WHERE p.razorpayPaymentId = :razorpayPaymentId
        """)
    Optional<Payment> findByRazorpayPaymentIdForUpdate(
            @Param("razorpayPaymentId") String razorpayPaymentId
    );
}