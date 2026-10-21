-- Existing checkout sessions are cart-based; preserve their semantics.
ALTER TABLE checkout_sessions
  ADD COLUMN checkout_source VARCHAR(16) NOT NULL DEFAULT 'CART';
