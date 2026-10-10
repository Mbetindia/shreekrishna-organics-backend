package com.shreekrishna.organics.combo.entity;

import com.shreekrishna.organics.product.entity.ProductVariant;
import jakarta.persistence.*;

@Entity
@Table(name = "combo_offer_items")
public class ComboOfferItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combo_offer_id", nullable = false)
    private ComboOffer comboOffer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(nullable = false)
    private Integer quantity = 1;

    public ComboOfferItem() {
    }

    public Long getId() {
        return id;
    }

    public ComboOffer getComboOffer() {
        return comboOffer;
    }

    public void setComboOffer(ComboOffer comboOffer) {
        this.comboOffer = comboOffer;
    }

    public ProductVariant getProductVariant() {
        return productVariant;
    }

    public void setProductVariant(ProductVariant productVariant) {
        this.productVariant = productVariant;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}