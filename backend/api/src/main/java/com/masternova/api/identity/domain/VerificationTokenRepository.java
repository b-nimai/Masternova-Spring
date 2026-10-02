package com.masternova.api.identity.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

  Optional<VerificationToken> findByTokenHash(String tokenHash);
}
