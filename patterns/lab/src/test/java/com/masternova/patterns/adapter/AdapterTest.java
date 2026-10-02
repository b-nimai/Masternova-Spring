package com.masternova.patterns.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AdapterTest {

  static final String BOUNCES = "nobody@example.com";

  /** ⭐ ONE contract test, run against EVERY adapter: that is what "interchangeable" means. */
  static Stream<Function<Set<String>, Mailer>> adapters() {
    return Stream.of(
        bad -> new SmtpMailer(new LegacySmtpClient(bad)),
        bad -> new AcmeMailer(new AcmeMailApi(bad)));
  }

  @ParameterizedTest
  @MethodSource("adapters")
  void everyAdapterReturnsAMessageIdOnSuccess(Function<Set<String>, Mailer> factory) {
    Mailer mailer = factory.apply(Set.of(BOUNCES));

    String id = mailer.send(new Mailer.Email("ada@example.com", "Hi", "Hello Ada"));

    assertThat(id).isNotBlank();
  }

  @ParameterizedTest
  @MethodSource("adapters")
  void everyAdapterTranslatesAPermanentRefusalIntoRejected(Function<Set<String>, Mailer> factory) {
    Mailer mailer = factory.apply(Set.of(BOUNCES));

    assertThatThrownBy(() -> mailer.send(new Mailer.Email(BOUNCES, "Hi", "Hello")))
        .isInstanceOf(Mailer.Rejected.class);
  }

  @Test
  void smtpAdapterBuildsMimeAndExtractsTheQueueId() {
    LegacySmtpClient client = new LegacySmtpClient(Set.of());

    String id = new SmtpMailer(client).send(new Mailer.Email("ada@example.com", "Hi", "Body"));

    assertThat(id).isEqualTo("Q1");
    assertThat(client.transcript().getFirst())
        .contains("To: ada@example.com\r\n")
        .contains("Subject: Hi\r\n")
        .endsWith("\r\n\r\nBody");
  }

  @Test
  void aCheckedConnectionFailureBecomesATemporaryUncheckedOne() {
    LegacySmtpClient client = new LegacySmtpClient(Set.of());
    client.simulateConnectionDown();

    assertThatThrownBy(() -> new SmtpMailer(client).send(new Mailer.Email("a@b.c", "s", "b")))
        .isInstanceOf(UncheckedIOException.class)
        .isNotInstanceOf(Mailer.Rejected.class);
  }

  @Test
  void aVendorOutageStaysTemporarySoTheAddressIsNeverSuppressedForIt() {
    AcmeMailApi api = new AcmeMailApi(Set.of());
    api.simulateOutage();

    assertThatThrownBy(() -> new AcmeMailer(api).send(new Mailer.Email("a@b.c", "s", "b")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("503")
        .isNotInstanceOf(Mailer.Rejected.class);
  }
}
