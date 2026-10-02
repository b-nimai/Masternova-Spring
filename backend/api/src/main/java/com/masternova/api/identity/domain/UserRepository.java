package com.masternova.api.identity.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data generates the implementation (a proxy — note 09). */
public interface UserRepository extends JpaRepository<User, UUID> {

  /** Pass the NORMALISED email (Email.value()). */
  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);
}
