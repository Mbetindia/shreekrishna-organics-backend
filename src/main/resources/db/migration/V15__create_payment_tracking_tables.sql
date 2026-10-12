-- ShreeKrishna Organics
-- Payment attempts, webhook events and refund tracking
-- MySQL 8 / Flyway V15

CREATE TABLE payment_attempts (
                                  id BIGINT NOT NULL AUTO_INCREMENT,
                                  payment_id BIGINT NOT NULL,
                                  razorpay_payment_id VARCHAR(100) NOT NULL,
                                  status VARCHAR(30) NOT NULL,
                                  amount DECIMAL(12,2) NOT NULL,
                                  currency VARCHAR(3) NOT NULL DEFAULT 'INR',
                                  failure_code VARCHAR(100) NULL,
                                  failure_description VARCHAR(500) NULL,
                                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

                                  PRIMARY KEY (id),
                                  CONSTRAINT fk_payment_attempts_payment
                                      FOREIGN KEY (payment_id) REFERENCES payments(id),
                                  CONSTRAINT uk_payment_attempts_razorpay_payment
                                      UNIQUE (razorpay_payment_id),
                                  CONSTRAINT chk_payment_attempts_amount
                                      CHECK (amount > 0),

                                  INDEX idx_payment_attempts_payment (payment_id),
                                  INDEX idx_payment_attempts_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE razorpay_webhook_events (
                                         id BIGINT NOT NULL AUTO_INCREMENT,
                                         event_id VARCHAR(150) NOT NULL,
                                         event_type VARCHAR(100) NOT NULL,
                                         processing_status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
                                         created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                         processed_at DATETIME(6) NULL,

                                         PRIMARY KEY (id),
                                         CONSTRAINT uk_razorpay_webhook_event_id UNIQUE (event_id),

                                         INDEX idx_webhook_processing_status (processing_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE payment_refunds (
                                 id BIGINT NOT NULL AUTO_INCREMENT,
                                 payment_id BIGINT NOT NULL,
                                 razorpay_refund_id VARCHAR(100) NOT NULL,
                                 razorpay_payment_id VARCHAR(100) NOT NULL,
                                 amount DECIMAL(12,2) NOT NULL,
                                 currency VARCHAR(3) NOT NULL DEFAULT 'INR',
                                 status VARCHAR(30) NOT NULL,
                                 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

                                 PRIMARY KEY (id),
                                 CONSTRAINT fk_payment_refunds_payment
                                     FOREIGN KEY (payment_id) REFERENCES payments(id),
                                 CONSTRAINT uk_payment_refunds_razorpay_refund
                                     UNIQUE (razorpay_refund_id),
                                 CONSTRAINT chk_payment_refunds_amount
                                     CHECK (amount > 0),

                                 INDEX idx_payment_refunds_payment (payment_id),
                                 INDEX idx_payment_refunds_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;