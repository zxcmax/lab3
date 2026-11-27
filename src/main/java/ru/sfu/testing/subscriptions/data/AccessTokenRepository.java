package ru.sfu.testing.subscriptions.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccessTokenRepository extends JpaRepository<AccessTokenEntity, String> {
    @Query("""
            SELECT token.accountId FROM AccessTokenEntity token
            WHERE token.tokenHash = :hash AND token.expiresAt > :now
            """)
    Optional<UUID> findActiveAccountId(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM AccessTokenEntity token WHERE token.expiresAt <= :now")
    void deleteExpired(@Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM AccessTokenEntity token WHERE token.tokenHash = :hash")
    void revoke(@Param("hash") String hash);
}
