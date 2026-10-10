package com.shreekrishna.organics.combo.controller;

import com.shreekrishna.organics.combo.dto.ComboOfferRequest;
import com.shreekrishna.organics.combo.dto.ComboOfferResponse;
import com.shreekrishna.organics.combo.service.ComboOfferService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

import com.shreekrishna.organics.combo.dto.ComboItemRequest;
import java.util.Map;
@RestController
@RequestMapping("/api/admin/combos")
public class ComboOfferController {

    private final ComboOfferService comboOfferService;

    public ComboOfferController(ComboOfferService comboOfferService) {
        this.comboOfferService = comboOfferService;
    }

    @PostMapping
    public ResponseEntity<ComboOfferResponse> createCombo(
            @RequestBody ComboOfferRequest request) {

        ComboOfferResponse response =
                comboOfferService.createCombo(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
        @PatchMapping("/{comboId}/price")
        public ResponseEntity<ComboOfferResponse> updateComboPrice(
                @PathVariable Long comboId,
                @RequestBody BigDecimal newPrice) {

            ComboOfferResponse response =
                    comboOfferService.updateComboPrice(comboId, newPrice);

            return ResponseEntity.ok(response);
        }
    @GetMapping("/{comboId}")
    public ResponseEntity<ComboOfferResponse> getComboById(
            @PathVariable Long comboId) {

        ComboOfferResponse response =
                comboOfferService.getComboById(comboId);

        return ResponseEntity.ok(response);
    }
    @GetMapping
    public ResponseEntity<List<ComboOfferResponse>> getActiveCombos() {

        List<ComboOfferResponse> combos =
                comboOfferService.getActiveCombos();

        return ResponseEntity.ok(combos);
    }
    @PatchMapping("/{comboId}/deactivate")
    public ResponseEntity<ComboOfferResponse> deactivateCombo(
            @PathVariable Long comboId) {

        ComboOfferResponse response =
                comboOfferService.deactivateCombo(comboId);

        return ResponseEntity.ok(response);
    }
    @PatchMapping("/{comboId}/activate")
    public ResponseEntity<ComboOfferResponse> activateCombo(
            @PathVariable Long comboId) {

        ComboOfferResponse response =
                comboOfferService.activateCombo(comboId);

        return ResponseEntity.ok(response);
    }
    @PatchMapping("/{comboId}/details")
    public ResponseEntity<ComboOfferResponse> updateComboDetails(
            @PathVariable Long comboId,
            @RequestBody Map<String, String> request) {

        ComboOfferResponse response =
                comboOfferService.updateComboDetails(
                        comboId,
                        request.get("name"),
                        request.get("description")
                );

        return ResponseEntity.ok(response);
    }
    @PutMapping("/{comboId}/items")
    public ResponseEntity<ComboOfferResponse> updateComboItems(
            @PathVariable Long comboId,
            @RequestBody List<ComboItemRequest> items) {

        ComboOfferResponse response =
                comboOfferService.updateComboItems(comboId, items);

        return ResponseEntity.ok(response);
    }
    }
