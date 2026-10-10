package com.shreekrishna.organics.combo.dto;

import java.math.BigDecimal;
import java.util.List;

public class ComboOfferRequest {

    private String name;

    private String description;

    private BigDecimal comboPrice;

    private Boolean active;

    private List<ComboItemRequest> items;

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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public List<ComboItemRequest> getItems() {
        return items;
    }

    public void setItems(List<ComboItemRequest> items) {
        this.items = items;
    }
}