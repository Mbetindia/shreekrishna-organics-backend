CREATE TABLE combo_offers (
                              id BIGINT NOT NULL AUTO_INCREMENT,
                              name VARCHAR(255) NOT NULL,
                              description VARCHAR(255),
                              combo_price DECIMAL(10, 2) NOT NULL,
                              active BOOLEAN NOT NULL DEFAULT TRUE,

                              PRIMARY KEY (id)
);

CREATE TABLE combo_offer_items (
                                   id BIGINT NOT NULL AUTO_INCREMENT,
                                   combo_offer_id BIGINT NOT NULL,
                                   product_variant_id BIGINT NOT NULL,
                                   quantity INT NOT NULL,

                                   PRIMARY KEY (id),

                                   CONSTRAINT fk_combo_offer_items_combo
                                       FOREIGN KEY (combo_offer_id)
                                           REFERENCES combo_offers(id),

                                   CONSTRAINT fk_combo_offer_items_variant
                                       FOREIGN KEY (product_variant_id)
                                           REFERENCES product_variants(id),

                                   CONSTRAINT chk_combo_offer_item_quantity
                                       CHECK (quantity > 0),

                                   CONSTRAINT uq_combo_offer_variant
                                       UNIQUE (combo_offer_id, product_variant_id)
);