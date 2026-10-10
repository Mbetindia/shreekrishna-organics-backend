package com.shreekrishna.organics.combo.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "combo_offers")
public class ComboOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "combo_price", nullable = false,
            precision = 10, scale = 2)
    private BigDecimal comboPrice;

    @Column(nullable = false)
    private boolean active = true;

    public ComboOffer() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getComboPrice() {
        return comboPrice;
    }

    public void setComboPrice(BigDecimal comboPrice) {
        this.comboPrice = comboPrice;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}