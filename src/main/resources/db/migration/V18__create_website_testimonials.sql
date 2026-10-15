
CREATE TABLE website_testimonials (
    id BIGINT NOT NULL AUTO_INCREMENT,

    customer_name VARCHAR(120) NOT NULL,

    comment VARCHAR(2000) NOT NULL,

    rating INT NOT NULL,

    image_url VARCHAR(2048) NULL,

    image_public_id VARCHAR(255) NULL,

    display_order INT NOT NULL,

    active BIT(1) NOT NULL DEFAULT b'1',

    PRIMARY KEY (id),

    CONSTRAINT uk_website_testimonials_display_order
        UNIQUE (display_order),

    CONSTRAINT chk_website_testimonials_rating
        CHECK (rating BETWEEN 1 AND 5)
);
