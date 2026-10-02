package com.masternova.api.identity.infrastructure;

import com.masternova.api.identity.UserRegistered;
import com.masternova.api.platform.MasternovaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * DEV CONVENIENCE until the notification module sends real emails (Phase 4): logs the verification
 * link after the signup COMMITS. Off by default — logging a live token in production would be a
 * security incident.
 */
@Component
@ConditionalOnBooleanProperty("masternova.identity.log-verification-links")
class VerificationLinkLogger {

  private static final Logger log = LoggerFactory.getLogger(VerificationLinkLogger.class);
  private final MasternovaProperties properties;

  VerificationLinkLogger(MasternovaProperties properties) {
    this.properties = properties;
  }

  @TransactionalEventListener // AFTER_COMMIT: never log a link for a signup that rolled back
  void onRegistered(UserRegistered event) {
    log.info(
        "[dev] verify {}: {}/verify-email?token={}",
        event.email(),
        properties.webUrl(),
        event.verificationToken());
  }
}
