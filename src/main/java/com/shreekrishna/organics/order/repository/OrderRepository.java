package com.shreekrishna.organics.order.repository;

import com.shreekrishna.organics.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Order> findByIdAndUserId(Long id, Long userId);
    Optional<Order> findByOrderNumberAndUserId(String orderNumber, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId and o.userId = :userId")
    Optional<Order> findOwnedOrderForUpdate(@Param("orderId") Long orderId, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);
}
