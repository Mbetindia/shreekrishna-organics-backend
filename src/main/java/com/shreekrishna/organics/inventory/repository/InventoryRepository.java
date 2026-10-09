
package com.shreekrishna.organics.inventory.repository;

import com.shreekrishna.organics.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository
        extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByVariant_Id(Long variantId);

    boolean existsByVariant_Id(Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           SELECT i
           FROM Inventory i
           WHERE i.variant.id = :variantId
           """)
    Optional<Inventory> findWithLockByVariantId(
            @Param("variantId") Long variantId
    );
}
