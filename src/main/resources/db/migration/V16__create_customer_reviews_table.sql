CREATE TABLE customer_reviews (
                                  id BIGINT NOT NULL AUTO_INCREMENT,
                                  product_id BIGINT NOT NULL,
                                  customer_name VARCHAR(150) NOT NULL,
                                  rating INT NOT NULL,
                                  comment TEXT,
                                  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

                                  PRIMARY KEY (id),

                                  CONSTRAINT fk_customer_reviews_product
                                      FOREIGN KEY (product_id)
                                          REFERENCES products(id),

                                  CONSTRAINT chk_customer_reviews_rating
                                      CHECK (rating BETWEEN 1 AND 5),

                                  CONSTRAINT chk_customer_reviews_status
                                      CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_customer_reviews_product_status
    ON customer_reviews(product_id, status);