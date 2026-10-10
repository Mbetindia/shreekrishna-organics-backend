package com.shreekrishna.organics.combo.repository;

import com.shreekrishna.organics.combo.entity.ComboOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComboOfferRepository
        extends JpaRepository<ComboOffer, Long> {

    List<ComboOffer> findByActiveTrue();
}