package com.shreekrishna.organics.offer.controller;

import com.shreekrishna.organics.offer.entity.ProductOffer;
import com.shreekrishna.organics.offer.service.ProductOfferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class ProductOfferController {

    private final ProductOfferService offerService;

    public ProductOfferController(ProductOfferService offerService) {
        this.offerService = offerService;
    }

    // ADMIN - CREATE OFFER
    @PostMapping("/admin/offers")
    public ResponseEntity<ProductOffer> createOffer(@RequestBody ProductOffer offer) {
        ProductOffer savedOffer = offerService.createOffer(offer);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedOffer);
    }

    // ADMIN - GET ALL OFFERS
    @GetMapping("/admin/offers")
    public ResponseEntity<List<ProductOffer>> getAllOffers() {
        return ResponseEntity.ok(offerService.getAllOffers());
    }

    // ADMIN - GET OFFERS BY PRODUCT
    @GetMapping("/admin/offers/product/{productId}")
    public ResponseEntity<List<ProductOffer>> getOffersByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(offerService.getOffersByProduct(productId));
    }

    // ADMIN - GET OFFER BY ID
    @GetMapping("/admin/offers/{id}")
    public ResponseEntity<ProductOffer> getOfferById(@PathVariable Long id) {
        return ResponseEntity.ok(offerService.getOfferById(id));
    }

    // ADMIN - UPDATE OFFER
    @PutMapping("/admin/offers/{id}")
    public ResponseEntity<ProductOffer> updateOffer(
            @PathVariable Long id,
            @RequestBody ProductOffer request) {
        return ResponseEntity.ok(offerService.updateOffer(id, request));
    }

    // ADMIN - ACTIVATE OFFER
    @PatchMapping("/admin/offers/{id}/activate")
    public ResponseEntity<ProductOffer> activateOffer(@PathVariable Long id) {
        return ResponseEntity.ok(offerService.activateOffer(id));
    }

    // ADMIN - DEACTIVATE OFFER
    @PatchMapping("/admin/offers/{id}/deactivate")
    public ResponseEntity<ProductOffer> deactivateOffer(@PathVariable Long id) {
        return ResponseEntity.ok(offerService.deactivateOffer(id));
    }

    // PUBLIC - GET CURRENT ACTIVE OFFER
    @GetMapping("/products/{productId}/offer")
    public ResponseEntity<ProductOffer> getActiveOffer(@PathVariable Long productId) {
        Optional<ProductOffer> offer = offerService.getActiveOffer(productId);
        return offer.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
