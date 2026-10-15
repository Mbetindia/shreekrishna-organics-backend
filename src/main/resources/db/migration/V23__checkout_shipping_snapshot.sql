-- Stage 5A: new checkout drafts copy the delivery address at creation time.
-- Existing CREATED drafts do not have snapshots: abandon and recreate those drafts after deployment.
-- Existing PAYMENT_PENDING sessions need manual reconciliation; do NOT run backfill blindly.
ALTER TABLE checkout_sessions
 ADD COLUMN shipping_full_name VARCHAR(150) NULL,
 ADD COLUMN shipping_mobile VARCHAR(20) NULL,
 ADD COLUMN shipping_address_line1 VARCHAR(255) NULL,
 ADD COLUMN shipping_address_line2 VARCHAR(255) NULL,
 ADD COLUMN shipping_landmark VARCHAR(255) NULL,
 ADD COLUMN shipping_city VARCHAR(100) NULL,
 ADD COLUMN shipping_state VARCHAR(100) NULL,
 ADD COLUMN shipping_postal_code VARCHAR(10) NULL,
 ADD COLUMN shipping_country VARCHAR(100) NULL;
