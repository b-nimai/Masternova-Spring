package com.masternova.worker.notification.mail;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;

/**
 * ⭐ The TARGET interface of the Adapter pattern: what the notification pipeline needs from "a way
 * to send email" — nothing more. Each provider's own API (SMTP sessions, Resend's JSON over HTTP)
 * is adapted to this, so swapping providers is a property, not a code change.
 *
 * <p>Failure contract: throw {@link PermanentDeliveryException} when retrying can never succeed
 * (unknown mailbox, rejected address) — anything else is treated as temporary and retried.
 */
@DesignPattern(value = Pattern.ADAPTER, role = "Target", note = "patterns/docs/12-adapter.md")
public interface MailProvider {

  /**
   * @return the provider's message id (stored on the delivery row for support and bounce matching)
   */
  String send(OutboundEmail email);
}
