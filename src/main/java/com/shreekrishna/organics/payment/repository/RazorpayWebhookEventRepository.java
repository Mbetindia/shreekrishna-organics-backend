package com.shreekrishna.organics.payment.repository;

import com.shreekrishna.organics.payment.entity.RazorpayWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface RazorpayWebhookEventRepository
        extends JpaRepository<RazorpayWebhookEvent, Long> {

    Optional<RazorpayWebhookEvent> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e FROM RazorpayWebhookEvent e
            WHERE e.eventId = :eventId
            """)
    Optional<RazorpayWebhookEvent> findByEventIdForUpdate(
            @Param("eventId") String eventId
    );
}