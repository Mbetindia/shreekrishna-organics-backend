package com.shreekrishna.organics.combo.dto;

import java.math.BigDecimal;
import java.util.List;

public class ComboOfferResponse {

    private Long id;
    private String name;
    private String description;
    private BigDecimal comboPrice;
    private boolean active;
    private List<ComboItemResponse> items;

    public ComboOfferResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public List<ComboItemResponse> getItems() {
        return items;
    }

    public void setItems(List<ComboItemResponse> items) {
        this.items = items;
    }
}