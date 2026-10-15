
CREATE TABLE variant_delivery_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    variant_id BIGINT NOT NULL,
    min_quantity INT NOT NULL,
    max_quantity INT NULL,
    delivery_charge DECIMAL(12, 2) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_variant_delivery_rule_variant
        FOREIGN KEY (variant_id)
        REFERENCES product_variants(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_delivery_min_quantity
        CHECK (min_quantity >= 1),

    CONSTRAINT chk_delivery_max_quantity
        CHECK (
            max_quantity IS NULL
            OR max_quantity >= min_quantity
        ),

    CONSTRAINT chk_delivery_charge_nonnegative
        CHECK (delivery_charge >= 0),

    CONSTRAINT uq_variant_delivery_min_quantity
        UNIQUE (variant_id, min_quantity)
);

CREATE INDEX idx_variant_delivery_rules_lookup
    ON variant_delivery_rules (variant_id, min_quantity, max_quantity);
