CREATE TABLE blogs (
    id BIGINT NOT NULL AUTO_INCREMENT,

    title VARCHAR(255) NOT NULL,

    slug VARCHAR(255) NOT NULL,

    content LONGTEXT NOT NULL,

    image_url VARCHAR(2048),

    image_public_id VARCHAR(255),

    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    published_at DATETIME(6) NULL,

    PRIMARY KEY (id),

    UNIQUE KEY uk_blogs_slug (slug),

    INDEX idx_blogs_status (status),

    INDEX idx_blogs_published_at (published_at)
);