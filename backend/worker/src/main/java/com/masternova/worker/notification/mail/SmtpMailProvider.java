package com.masternova.worker.notification.mail;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * ADAPTER: {@link MailProvider} over SMTP via Spring's {@link JavaMailSender} (Mailpit in dev, any
 * SMTP relay — SES, Postmark — in prod). Default provider.
 */
@Component
@ConditionalOnProperty(
    name = "masternova.notification.provider",
    havingValue = "smtp",
    matchIfMissing = true)
@DesignPattern(value = Pattern.ADAPTER, role = "Adapter", note = "patterns/docs/12-adapter.md")
class SmtpMailProvider implements MailProvider {

  private final JavaMailSender mailSender;

  SmtpMailProvider(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  @Override
  public String send(OutboundEmail email) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      // multipart/alternative: clients show the HTML part and fall back to the text part
      MimeMessageHelper helper =
          new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
      helper.setFrom(email.from());
      helper.setTo(email.to());
      helper.setSubject(email.subject());
      helper.setText(email.text(), email.html());
      for (var header : email.headers().entrySet()) {
        message.setHeader(header.getKey(), header.getValue());
      }
      mailSender.send(message);
      return message.getMessageID(); // set by saveChanges() during send
    } catch (MailSendException e) {
      throw permanentIfRejected(e);
    } catch (MessagingException e) {
      throw new IllegalStateException("could not build the email: " + e.getMessage(), e);
    }
  }

  /** ⭐ Translate the provider's failure into OUR failure contract — part of adapting. */
  static RuntimeException permanentIfRejected(MailSendException e) {
    for (Exception failure : e.getMessageExceptions()) {
      for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
        if (cause instanceof SMTPAddressFailedException rejected
            && rejected.getReturnCode() >= 500) {
          return new PermanentDeliveryException(
              "SMTP " + rejected.getReturnCode() + ": " + rejected.getMessage(), e);
        }
      }
    }
    return e; // connection refused, 4xx greylisting, timeouts … → temporary, retried
  }
}
