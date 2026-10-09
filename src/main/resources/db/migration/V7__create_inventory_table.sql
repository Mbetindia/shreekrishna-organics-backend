
CREATE TABLE inventory (
                           id BIGINT NOT NULL AUTO_INCREMENT,
                           variant_id BIGINT NOT NULL,

                           quantity INT NOT NULL DEFAULT 0,
                           reserved_quantity INT NOT NULL DEFAULT 0,

                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP,

                           PRIMARY KEY (id),

                           CONSTRAINT uk_inventory_variant
                               UNIQUE (variant_id),

                           CONSTRAINT fk_inventory_variant
                               FOREIGN KEY (variant_id)
                                   REFERENCES product_variants(id),

                           CONSTRAINT chk_inventory_quantity
                               CHECK (quantity >= 0),

                           CONSTRAINT chk_inventory_reserved
                               CHECK (reserved_quantity >= 0),

                           CONSTRAINT chk_inventory_reservation_limit
                               CHECK (reserved_quantity <= quantity)
);
