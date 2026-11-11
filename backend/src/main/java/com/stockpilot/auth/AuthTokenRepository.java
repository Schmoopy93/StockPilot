package com.stockpilot.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthTokenRepository extends JpaRepository<AuthToken, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from AuthToken t
            where t.tokenHash = :tokenHash
              and t.purpose = :purpose
              and t.consumedAt is null
              and t.expiresAt > :now
            """)
    Optional<AuthToken> findUsableForUpdate(@Param("tokenHash") String tokenHash,
                                            @Param("purpose") AuthTokenPurpose purpose,
                                            @Param("now") Instant now);

    void deleteByUserIdAndPurpose(UUID userId, AuthTokenPurpose purpose);
}
