
CREATE TABLE product_variants (
                                  id BIGINT NOT NULL AUTO_INCREMENT,
                                  product_id BIGINT NOT NULL,

                                  sku VARCHAR(100) NOT NULL,

                                  size_value DECIMAL(10,2) NOT NULL,
                                  size_unit VARCHAR(20) NOT NULL,

                                  price DECIMAL(10,2) NOT NULL,
                                  mrp DECIMAL(10,2),

                                  active BOOLEAN NOT NULL DEFAULT TRUE,

                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

                                  PRIMARY KEY (id),

                                  CONSTRAINT uk_product_variants_sku UNIQUE (sku),

                                  CONSTRAINT fk_product_variants_product
                                      FOREIGN KEY (product_id)
                                          REFERENCES products(id),

                                  CONSTRAINT chk_variant_size_positive
                                      CHECK (size_value > 0),

                                  CONSTRAINT chk_variant_price_nonnegative
                                      CHECK (price >= 0),

                                  CONSTRAINT chk_variant_mrp_nonnegative
                                      CHECK (mrp IS NULL OR mrp >= 0),

                                  INDEX idx_product_variants_product_id (product_id),
                                  INDEX idx_product_variants_active (active)
);
