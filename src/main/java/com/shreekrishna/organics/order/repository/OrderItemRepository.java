package com.shreekrishna.organics.order.repository;

import com.shreekrishna.organics.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrder_IdOrderByIdAsc(Long orderId);
}
