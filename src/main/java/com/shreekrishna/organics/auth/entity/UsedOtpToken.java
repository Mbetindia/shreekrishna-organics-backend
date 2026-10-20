
package com.shreekrishna.organics.auth.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "used_otp_tokens",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_used_otp_tokens_hash",
                        columnNames = "token_hash"
                )
        }
)
public class UsedOtpToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "token_hash",
            nullable = false,
            unique = true,
            length = 64
    )
    private String tokenHash;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    public UsedOtpToken() {
    }

    public UsedOtpToken(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Long getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
