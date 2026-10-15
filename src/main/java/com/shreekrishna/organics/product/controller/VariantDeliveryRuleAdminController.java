
package com.shreekrishna.organics.product.controller;

import com.shreekrishna.organics.product.dto.VariantDeliveryRuleRequest;
import com.shreekrishna.organics.product.entity.VariantDeliveryRule;
import com.shreekrishna.organics.product.service.VariantDeliveryRuleAdminService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/variants")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class VariantDeliveryRuleAdminController {

    private final VariantDeliveryRuleAdminService service;

    public VariantDeliveryRuleAdminController(
            VariantDeliveryRuleAdminService service
    ) {
        this.service = service;
    }

    @GetMapping("/{variantId}/delivery-rules")
    public ResponseEntity<List<DeliveryRuleResponse>> getRules(
            @PathVariable Long variantId
    ) {
        List<DeliveryRuleResponse> rules = service.getRules(variantId)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(rules);
    }

    @PutMapping("/{variantId}/delivery-rules")
    public ResponseEntity<Void> saveRules(
            @PathVariable Long variantId,
            @RequestBody
            @NotEmpty(message = "At least one rule is required")
            List<@Valid VariantDeliveryRuleRequest> requests
    ) {
        service.saveRules(variantId, requests);

        return ResponseEntity.noContent().build();
    }

    private DeliveryRuleResponse toResponse(VariantDeliveryRule rule) {
        return new DeliveryRuleResponse(
                rule.getId(),
                rule.getMinQuantity(),
                rule.getMaxQuantity(),
                rule.getDeliveryCharge()
        );
    }

    public record DeliveryRuleResponse(
            Long id,
            Integer minQuantity,
            Integer maxQuantity,
            BigDecimal deliveryCharge
    ) {
    }
}
