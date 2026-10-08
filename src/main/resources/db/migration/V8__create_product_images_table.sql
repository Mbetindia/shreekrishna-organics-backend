CREATE TABLE product_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    product_id BIGINT NOT NULL,

    image_url VARCHAR(2048) NOT NULL,

    public_id VARCHAR(255) NOT NULL,

    is_primary BOOLEAN NOT NULL DEFAULT FALSE,

    display_order INT NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_product_images_public_id
        UNIQUE (public_id),

    CONSTRAINT fk_product_images_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE CASCADE,

    INDEX idx_product_images_product_id (product_id),

    INDEX idx_product_images_product_order
        (product_id, display_order)
);