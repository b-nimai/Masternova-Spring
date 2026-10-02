package com.masternova.worker.notification.mail;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.internet.InternetAddress;
import java.util.Map;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

/** The SMTP adapter's error translation: only a 5xx address rejection is permanent. */
class SmtpMailProviderTest {

  private static MailSendException rejected(int code) throws Exception {
    var failure =
        new SMTPAddressFailedException(
            new InternetAddress("gone@example.com"),
            "RCPT TO",
            code,
            code + " 5.1.1 <gone@example.com>: Recipient address rejected");
    return new MailSendException(Map.of(new Object(), failure));
  }

  @Test
  void a5xxRecipientRejectionIsPermanent() throws Exception {
    assertThat(SmtpMailProvider.permanentIfRejected(rejected(550)))
        .isInstanceOf(PermanentDeliveryException.class)
        .hasMessageStartingWith("SMTP 550");
  }

  @Test
  void a4xxIsTemporaryAndKeptAsIs() throws Exception {
    MailSendException greylisted = rejected(450);
    assertThat(SmtpMailProvider.permanentIfRejected(greylisted)).isSameAs(greylisted);
  }

  @Test
  void aConnectionProblemIsTemporary() {
    MailSendException down = new MailSendException("Mail server connection failed");
    assertThat(SmtpMailProvider.permanentIfRejected(down)).isSameAs(down);
  }
}
