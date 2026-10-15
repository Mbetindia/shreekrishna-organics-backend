package com.shreekrishna.organics.checkout.repository;

import com.shreekrishna.organics.checkout.entity.CheckoutSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, String> {
    Optional<CheckoutSession> findByIdAndUserId(String id, Long userId);
    Optional<CheckoutSession> findByRazorpayOrderId(String razorpayOrderId);
    boolean existsByUserIdAndStatus(Long userId, String status);
    boolean existsByUserIdAndStatusAndIdNot(Long userId, String status, String id);
    boolean existsByUserIdAndStatusInAndIdNot(Long userId, List<String> statuses, String id);
    List<CheckoutSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CheckoutSession s where s.id = :id and s.userId = :userId")
    Optional<CheckoutSession> findOwnedForUpdate(@Param("id") String id, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CheckoutSession s where s.id = :id")
    Optional<CheckoutSession> findByIdForUpdate(@Param("id") String id);
}
