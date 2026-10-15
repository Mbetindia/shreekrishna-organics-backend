package com.shreekrishna.organics.checkout.repository;

import com.shreekrishna.organics.checkout.entity.CheckoutSessionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CheckoutSessionItemRepository extends JpaRepository<CheckoutSessionItem, Long> {
    List<CheckoutSessionItem> findBySession_IdOrderByIdAsc(String sessionId);
}
