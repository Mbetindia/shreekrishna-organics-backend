
CREATE TABLE product_offers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,

    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(10,2) NOT NULL,

    start_date DATETIME NOT NULL,
    end_date DATETIME NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT fk_product_offers_product
        FOREIGN KEY (product_id)
        REFERENCES products(id),

    CONSTRAINT chk_offer_discount_type
        CHECK (discount_type IN ('PERCENTAGE', 'FIXED')),

    CONSTRAINT chk_offer_discount_positive
        CHECK (discount_value > 0),

    CONSTRAINT chk_offer_percentage_limit
        CHECK (
            discount_type <> 'PERCENTAGE'
            OR discount_value <= 100
        ),

    CONSTRAINT chk_offer_dates
        CHECK (end_date > start_date),

    INDEX idx_product_offers_product_id (product_id),
    INDEX idx_product_offers_active_dates
        (active, start_date, end_date)
);
