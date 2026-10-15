
package com.shreekrishna.organics.product.repository;

import com.shreekrishna.organics.product.entity.VariantDeliveryRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VariantDeliveryRuleRepository
        extends JpaRepository<VariantDeliveryRule, Long> {

    // एका Variant चे सर्व Delivery Rules मिळवण्यासाठी
    List<VariantDeliveryRule>
    findByVariantIdOrderByMinQuantityAsc(Long variantId);

    // एका Variant चे Rules Delete करण्यासाठी
    void deleteByVariantId(Long variantId);

}
