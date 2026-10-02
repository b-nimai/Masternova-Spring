package com.masternova.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.worker.MutableClock;
import com.masternova.worker.TestcontainersConfiguration;
import com.masternova.worker.notification.EmailDeliveries.Claim;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The delivery state machine against real Postgres and the REAL schema (the api's migrations, see
 * test resources config/application.yaml). Lease = 60 s.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"masternova.outbox.lease=60s", "masternova.outbox.relay-enabled=false"})
@Import({TestcontainersConfiguration.class, JdbcEmailDeliveriesIT.Config.class})
class JdbcEmailDeliveriesIT {

  @TestConfiguration(proxyBeanMethods = false)
  static class Config {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired EmailDeliveries deliveries;
  @Autowired MutableClock clock;
  @Autowired JdbcClient jdbc;

  private DeliveryKey key;

  @BeforeEach
  void freshKey() {
    clock.reset();
    key = new DeliveryKey(UUID.randomUUID(), "verify-email", "asha@example.com");
  }

  @Test
  void theFirstClaimWinsAndASecondOneSeesItInFlight() {
    assertThat(deliveries.claim(key)).isInstanceOf(Claim.Claimed.class);
    assertThat(deliveries.claim(key)).isInstanceOf(Claim.InFlight.class);
  }

  @Test
  void aFailedSendCanBeClaimedAgainOnTheSameRow() {
    Claim.Claimed first = (Claim.Claimed) deliveries.claim(key);
    deliveries.markFailed(first.deliveryId(), "SMTP down");

    Claim again = deliveries.claim(key);

    assertThat(again).isEqualTo(new Claim.Claimed(first.deliveryId(), 2)); // same row, attempt 2
  }

  @Test
  void aSentEmailIsNeverClaimedAgain() {
    Claim.Claimed claimed = (Claim.Claimed) deliveries.claim(key);
    deliveries.markSent(claimed.deliveryId(), "<msg-1@mailpit>");

    assertThat(deliveries.claim(key)).isInstanceOf(Claim.AlreadySent.class);
    assertThat(
            jdbc.sql("SELECT provider_message_id FROM email_delivery WHERE id = :id")
                .param("id", claimed.deliveryId())
                .query(String.class)
                .single())
        .isEqualTo("<msg-1@mailpit>");
  }

  @Test
  void aSendingClaimOlderThanTheLeaseIsTakenOver() {
    deliveries.claim(key); // a worker claimed it … and died mid-send
    clock.advance(Duration.ofSeconds(59));
    assertThat(deliveries.claim(key)).isInstanceOf(Claim.InFlight.class); // still within its lease

    clock.advance(Duration.ofSeconds(2));
    assertThat(deliveries.claim(key))
        .isInstanceOfSatisfying(Claim.Claimed.class, c -> assertThat(c.attempt()).isEqualTo(2));
  }

  @Test
  void suppressedAndBouncedAreFinal() {
    deliveries.recordSuppressed(key, "OPTED_OUT");
    deliveries.recordSuppressed(key, "OPTED_OUT"); // idempotent
    assertThat(deliveries.claim(key)).isEqualTo(new Claim.Final("SUPPRESSED"));

    DeliveryKey other = new DeliveryKey(UUID.randomUUID(), "verify-email", "gone@example.com");
    Claim.Claimed claimed = (Claim.Claimed) deliveries.claim(other);
    deliveries.markBounced(claimed.deliveryId(), "550 mailbox unavailable");
    assertThat(deliveries.claim(other)).isEqualTo(new Claim.Final("BOUNCED"));
  }

  @Test
  void theRecipientIsCaseInsensitive() {
    deliveries.claim(key);
    DeliveryKey shouting = new DeliveryKey(key.eventId(), key.template(), "ASHA@Example.COM");

    assertThat(deliveries.claim(shouting)).isInstanceOf(Claim.InFlight.class); // citext: same email
  }

  @Test
  void twentyConcurrentClaimsProduceExactlyOneSender() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Claim>> results;
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      results =
          java.util.stream.IntStream.range(0, 20)
              .mapToObj(
                  i ->
                      pool.submit(
                          () -> {
                            start.await();
                            return deliveries.claim(key);
                          }))
              .toList();
      start.countDown();
    }

    long senders = 0;
    for (Future<Claim> result : results) {
      if (result.get() instanceof Claim.Claimed) {
        senders++;
      }
    }
    assertThat(senders).isEqualTo(1); // ⭐ the unique index serialises them: one sender, 19 InFlight
  }
}
