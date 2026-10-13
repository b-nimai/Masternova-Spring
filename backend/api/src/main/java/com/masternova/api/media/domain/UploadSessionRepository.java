package com.masternova.api.media.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {

  /**
   * ⭐ {@code SELECT … FOR UPDATE}: serialises concurrent completes / aborts of ONE session (a
   * double click, a browser retry, the sweeper). The second waits, then sees the first's result.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from UploadSession s where s.id = :id")
  Optional<UploadSession> lockById(UUID id);
}
