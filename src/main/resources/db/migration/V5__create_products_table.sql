CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT,

    category_id BIGINT NOT NULL,

    name VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL,

    short_description VARCHAR(500),
    description TEXT,

    base_price DECIMAL(10,2) NOT NULL,
    mrp DECIMAL(10,2),

    active BOOLEAN NOT NULL DEFAULT TRUE,
    featured BOOLEAN NOT NULL DEFAULT FALSE,

    display_order INT NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT uk_products_slug UNIQUE (slug),

    CONSTRAINT fk_products_category
        FOREIGN KEY (category_id)
        REFERENCES categories(id),

    INDEX idx_products_category_id (category_id),
    INDEX idx_products_active (active),
    INDEX idx_products_featured (featured),
    INDEX idx_products_display_order (display_order)
);