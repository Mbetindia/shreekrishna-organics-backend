-- ShreeKrishna Organics: Razorpay payment transactions
-- MySQL 8 / Flyway V14

CREATE TABLE payments (
                          id BIGINT NOT NULL AUTO_INCREMENT,
                          order_id BIGINT NOT NULL,

                          provider VARCHAR(20) NOT NULL DEFAULT 'RAZORPAY',
                          status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

                          razorpay_order_id VARCHAR(100) NOT NULL,
                          razorpay_payment_id VARCHAR(100) NULL,

                          amount DECIMAL(12,2) NOT NULL,
                          currency VARCHAR(3) NOT NULL DEFAULT 'INR',

                          created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                          updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

                          PRIMARY KEY (id),

                          CONSTRAINT fk_payments_order
                              FOREIGN KEY (order_id) REFERENCES orders(id),

                          CONSTRAINT uk_payments_razorpay_order
                              UNIQUE (razorpay_order_id),

                          CONSTRAINT uk_payments_razorpay_payment
                              UNIQUE (razorpay_payment_id),

                          CONSTRAINT chk_payments_amount
                              CHECK (amount > 0),

                          INDEX idx_payments_order_id (order_id),
                          INDEX idx_payments_status (status)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;