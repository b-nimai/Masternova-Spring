package com.masternova.api.identity.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

  List<AuthSession> findByUserIdAndRevokedAtIsNull(UUID userId);
}
