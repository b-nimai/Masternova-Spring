package com.masternova.api.identity.infrastructure;

import com.masternova.api.identity.Role;
import com.masternova.api.identity.domain.Email;
import com.masternova.api.identity.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Someone has to be the first admin. On startup, if {@code
 * masternova.identity.bootstrap-admin-email} names an existing account, it gets ADMIN. Idempotent —
 * safe on every boot; a no-op when unset. (Startup work goes in an ApplicationRunner, not a
 * constructor — note 08 §6.)
 */
@Component
class AdminBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

  private final UserRepository users;
  private final String adminEmail;

  AdminBootstrap(
      UserRepository users,
      @Value("${masternova.identity.bootstrap-admin-email:}") String adminEmail) {
    this.users = users;
    this.adminEmail = adminEmail;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (adminEmail.isBlank()) {
      return;
    }
    users
        .findByEmail(new Email(adminEmail).value())
        .ifPresentOrElse(
            user -> {
              if (!user.roles().contains(Role.ADMIN)) {
                user.grant(Role.ADMIN);
                log.info("Granted ADMIN to bootstrap admin {}", adminEmail);
              }
            },
            () ->
                log.warn(
                    "Bootstrap admin {} has no account yet — sign up first, then restart",
                    adminEmail));
  }
}
