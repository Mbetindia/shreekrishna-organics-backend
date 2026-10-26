ALTER TABLE product_images
ADD COLUMN variant_id BIGINT NULL;

ALTER TABLE product_images
ADD CONSTRAINT fk_product_images_variant
FOREIGN KEY (variant_id)
REFERENCES product_variants(id);

ALTER TABLE product_images
ADD CONSTRAINT uk_product_images_variant
UNIQUE (variant_id);

CREATE INDEX idx_product_images_product_variant
ON product_images(product_id, variant_id);