
CREATE TABLE used_otp_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_used_otp_tokens_hash UNIQUE (token_hash)
);
