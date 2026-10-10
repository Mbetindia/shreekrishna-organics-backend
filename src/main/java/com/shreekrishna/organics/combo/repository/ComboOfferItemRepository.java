package com.shreekrishna.organics.combo.repository;

import com.shreekrishna.organics.combo.entity.ComboOfferItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComboOfferItemRepository
        extends JpaRepository<ComboOfferItem, Long> {

    List<ComboOfferItem> findByComboOfferId(Long comboOfferId);
    void deleteByComboOfferId(Long comboOfferId);
}