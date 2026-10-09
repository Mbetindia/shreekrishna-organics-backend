package com.shreekrishna.organics.cart.repository;

import com.shreekrishna.organics.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository
        extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCartIdOrderByIdAsc(Long cartId);

    Optional<CartItem> findByCartIdAndVariantId(
            Long cartId,
            Long variantId
    );

    Optional<CartItem> findByIdAndCartId(
            Long itemId,
            Long cartId
    );

    void deleteByCartId(Long cartId);
}