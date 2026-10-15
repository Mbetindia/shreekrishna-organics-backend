
package com.shreekrishna.organics.product.service;

import com.shreekrishna.organics.product.dto.VariantDeliveryRuleRequest;
import com.shreekrishna.organics.product.entity.ProductVariant;
import com.shreekrishna.organics.product.entity.VariantDeliveryRule;
import com.shreekrishna.organics.product.repository.ProductVariantRepository;
import com.shreekrishna.organics.product.repository.VariantDeliveryRuleRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class VariantDeliveryRuleAdminService {

    private final VariantDeliveryRuleRepository ruleRepository;
    private final ProductVariantRepository variantRepository;

    public VariantDeliveryRuleAdminService(
            VariantDeliveryRuleRepository ruleRepository,
            ProductVariantRepository variantRepository
    ) {
        this.ruleRepository = ruleRepository;
        this.variantRepository = variantRepository;
    }

    @Transactional(readOnly = true)
    public List<VariantDeliveryRule> getRules(Long variantId) {
        ensureVariantExists(variantId);

        return ruleRepository
                .findByVariantIdOrderByMinQuantityAsc(variantId);
    }

    @Transactional
    public void saveRules(
            Long variantId,
            List<VariantDeliveryRuleRequest> requests
    ) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product variant not found"
                ));

        if (requests == null || requests.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one delivery rule is required"
            );
        }

        List<VariantDeliveryRuleRequest> sorted =
                new ArrayList<>(requests);

        if (sorted.stream().anyMatch(r -> r == null
                || r.minQuantity() == null
                || r.deliveryCharge() == null
                || r.minQuantity() < 1
                || (r.maxQuantity() != null && r.maxQuantity() < r.minQuantity())
                || r.deliveryCharge().signum() < 0
                || r.deliveryCharge().scale() > 2
                || r.deliveryCharge().precision() - r.deliveryCharge().scale() > 10)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid delivery rule"
            );
        }

        sorted.sort(Comparator.comparing(
                VariantDeliveryRuleRequest::minQuantity
        ));

        // Every quantity must match exactly one rule.
        // The first range starts at 1; the last is open-ended.
        long expectedMin = 1;

        for (int i = 0; i < sorted.size(); i++) {
            VariantDeliveryRuleRequest request = sorted.get(i);

            if (request.minQuantity().longValue() != expectedMin) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Delivery ranges must be continuous and start at 1"
                );
            }

            if (request.maxQuantity() == null) {
                if (i != sorted.size() - 1) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Only the last range can have no maximum"
                    );
                }
            } else {
                expectedMin = (long) request.maxQuantity() + 1;
            }
        }

        if (sorted.get(sorted.size() - 1).maxQuantity() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Last delivery range must be open-ended"
            );
        }

        ruleRepository.deleteByVariantId(variantId);
        ruleRepository.flush();

        List<VariantDeliveryRule> newRules = new ArrayList<>();

        for (VariantDeliveryRuleRequest request : sorted) {
            VariantDeliveryRule rule = new VariantDeliveryRule();

            rule.setVariant(variant);
            rule.setMinQuantity(request.minQuantity());
            rule.setMaxQuantity(request.maxQuantity());
            rule.setDeliveryCharge(request.deliveryCharge());

            newRules.add(rule);
        }

        ruleRepository.saveAll(newRules);
    }

    private void ensureVariantExists(Long variantId) {
        if (variantId == null || !variantRepository.existsById(variantId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Product variant not found"
            );
        }
    }
}
