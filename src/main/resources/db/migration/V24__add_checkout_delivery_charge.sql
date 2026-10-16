ALTER TABLE checkout_sessions
ADD COLUMN delivery_charge DECIMAL(12,2)
NOT NULL DEFAULT 0.00;