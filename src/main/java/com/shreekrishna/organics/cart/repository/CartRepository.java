package com.shreekrishna.organics.cart.repository;

import com.shreekrishna.organics.cart.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CartRepository
        extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserId(Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.userId = :userId")
    Optional<Cart> findByUserIdForUpdate(
            @Param("userId") Long userId
    );
}