package com.masternova.patterns.adapter;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * ADAPTER #1 (object adapter: it HAS-A {@link LegacySmtpClient}, it does not extend it). It
 * translates three things, which is the whole job of an adapter:
 *
 * <ol>
 *   <li>the request — our {@link Mailer.Email} → a raw MIME string;
 *   <li>the response — "250 … queued as Q1" → the id "Q1";
 *   <li>the failures — 5xx → {@link Mailer.Rejected}; checked {@link IOException} → unchecked
 *       (temporary).
 * </ol>
 */
public final class SmtpMailer implements Mailer {

  private final LegacySmtpClient client;

  public SmtpMailer(LegacySmtpClient client) {
    this.client = client;
  }

  @Override
  public String send(Email email) {
    String mime =
        "To: " + email.to() + "\r\nSubject: " + email.subject() + "\r\n\r\n" + email.body();
    String reply;
    try {
      reply = client.sendRaw(email.to(), mime);
    } catch (IOException e) {
      throw new UncheckedIOException(e); // ⭐ temporary: the caller retries
    }
    int code = Integer.parseInt(reply.substring(0, 3));
    if (code >= 500) {
      throw new Rejected("SMTP " + reply, null); // ⭐ permanent: the caller suppresses
    }
    if (code >= 400) {
      throw new IllegalStateException("SMTP " + reply); // 4xx greylisting → temporary
    }
    return reply.substring(reply.lastIndexOf(' ') + 1);
  }
}
