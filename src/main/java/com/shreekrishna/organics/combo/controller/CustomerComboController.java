package com.shreekrishna.organics.combo.controller;

import com.shreekrishna.organics.combo.dto.ComboOfferResponse;
import com.shreekrishna.organics.combo.service.ComboOfferService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@RestController
@RequestMapping("/api/combos")
public class CustomerComboController {

    private final ComboOfferService comboOfferService;

    public CustomerComboController(
            ComboOfferService comboOfferService) {

        this.comboOfferService = comboOfferService;
    }

    @GetMapping
    public ResponseEntity<List<ComboOfferResponse>> getActiveCombos() {

        List<ComboOfferResponse> combos =
                comboOfferService.getActiveCombos();

        return ResponseEntity.ok(combos);
    }
    @GetMapping("/{comboId}")
    public ResponseEntity<ComboOfferResponse> getComboById(
            @PathVariable Long comboId) {

        ComboOfferResponse response =
                comboOfferService.getComboById(comboId);

        if (!response.isActive()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }
}