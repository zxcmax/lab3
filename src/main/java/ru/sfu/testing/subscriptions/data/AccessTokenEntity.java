package ru.sfu.testing.subscriptions.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "access_tokens")
public class AccessTokenEntity {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected AccessTokenEntity() {
    }

    public AccessTokenEntity(String tokenHash, UUID accountId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.accountId = accountId;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
