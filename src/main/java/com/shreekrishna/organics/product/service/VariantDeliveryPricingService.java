
package com.shreekrishna.organics.product.service;

import com.shreekrishna.organics.product.entity.VariantDeliveryRule;
import com.shreekrishna.organics.product.repository.VariantDeliveryRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class VariantDeliveryPricingService {

    private final VariantDeliveryRuleRepository ruleRepository;

    public VariantDeliveryPricingService(
            VariantDeliveryRuleRepository ruleRepository
    ) {
        this.ruleRepository = ruleRepository;
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateDeliveryCharge(
            Long variantId,
            int quantity
    ) {
        if (variantId == null) {
            throw new IllegalArgumentException(
                    "Variant ID is required"
            );
        }

        if (quantity < 1) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }

        List<VariantDeliveryRule> rules =
                ruleRepository
                        .findByVariantIdOrderByMinQuantityAsc(variantId);

        for (VariantDeliveryRule rule : rules) {

            boolean minMatches =
                    quantity >= rule.getMinQuantity();

            boolean maxMatches =
                    rule.getMaxQuantity() == null
                            || quantity <= rule.getMaxQuantity();

            if (minMatches && maxMatches) {
                return rule.getDeliveryCharge();
            }
        }

        // No matching delivery rule configured.
        // Do not silently treat missing configuration as FREE.
        throw new IllegalStateException(
                "Delivery charge is not configured for variant "
                        + variantId
                        + " and quantity "
                        + quantity
        );
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateTotalDeliveryCharge(
            List<VariantQuantity> items
    ) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;

        for (VariantQuantity item : items) {
            if (item == null) {
                throw new IllegalArgumentException(
                        "Cart item cannot be null"
                );
            }

            BigDecimal charge = calculateDeliveryCharge(
                    item.variantId(),
                    item.quantity()
            );

            total = total.add(charge);
        }

        return total;
    }

    public record VariantQuantity(
            Long variantId,
            int quantity
    ) {}
}
