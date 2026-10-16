ALTER TABLE checkout_session_items
    ADD COLUMN gst_rate DECIMAL(5,2) NULL,
    ADD COLUMN price_includes_gst BOOLEAN NULL,
    ADD COLUMN tax_amount DECIMAL(12,2) NULL;