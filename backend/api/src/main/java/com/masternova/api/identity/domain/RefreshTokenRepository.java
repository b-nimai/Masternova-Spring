package com.masternova.api.identity.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * ⭐ Consumes the token ATOMICALLY: the row count says who won. 1 = this request may rotate it; 0
   * = it was already consumed (reuse!), has expired, or doesn't exist. Two concurrent refreshes can
   * never both get 1 (note 07 §5 — compare-and-set, done by the database).
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update RefreshToken t set t.consumedAt = :now"
          + " where t.tokenHash = :hash and t.consumedAt is null and t.expiresAt > :now")
  int consume(@Param("hash") String tokenHash, @Param("now") Instant now);
}
