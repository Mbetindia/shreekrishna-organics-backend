-- Does NOT create customer orders or clear cart.
-- The next phase will introduce authenticated session APIs and atomic payment finalization.
CREATE TABLE checkout_sessions (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  address_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
  currency VARCHAR(3) NOT NULL DEFAULT 'INR',
  subtotal DECIMAL(12,2) NOT NULL,
  total_amount DECIMAL(12,2) NOT NULL,
  razorpay_order_id VARCHAR(100) NULL,
  razorpay_payment_id VARCHAR(100) NULL,
  finalized_order_id BIGINT NULL,
  stock_reserved BOOLEAN NOT NULL DEFAULT FALSE,
  expires_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_checkout_sessions_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_checkout_sessions_address FOREIGN KEY (address_id) REFERENCES addresses(id),
  CONSTRAINT fk_checkout_sessions_final_order FOREIGN KEY (finalized_order_id) REFERENCES orders(id),
  CONSTRAINT uk_checkout_sessions_razorpay_order UNIQUE (razorpay_order_id),
  CONSTRAINT uk_checkout_sessions_razorpay_payment UNIQUE (razorpay_payment_id),
  CONSTRAINT uk_checkout_sessions_final_order UNIQUE (finalized_order_id),
  CONSTRAINT chk_checkout_sessions_amounts CHECK (subtotal >= 0 AND total_amount > 0),
  INDEX idx_checkout_sessions_user_created (user_id, created_at),
  INDEX idx_checkout_sessions_status_expiry (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE checkout_session_items (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  session_id VARCHAR(36) NOT NULL,
  variant_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_name VARCHAR(255) NOT NULL,
  sku VARCHAR(100) NOT NULL,
  size_value DECIMAL(10,2) NOT NULL,
  size_unit VARCHAR(20) NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(12,2) NOT NULL,
  unit_mrp DECIMAL(12,2) NULL,
  line_total DECIMAL(12,2) NOT NULL,
  CONSTRAINT fk_checkout_items_session FOREIGN KEY (session_id) REFERENCES checkout_sessions(id),
  CONSTRAINT fk_checkout_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id),
  CONSTRAINT chk_checkout_items_qty CHECK (quantity > 0),
  CONSTRAINT chk_checkout_items_line CHECK (unit_price >= 0 AND line_total >= 0),
  INDEX idx_checkout_items_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
