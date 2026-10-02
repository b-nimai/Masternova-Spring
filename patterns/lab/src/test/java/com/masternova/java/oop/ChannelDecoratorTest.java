package com.masternova.java.oop;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.java.oop.notify.Channel;
import com.masternova.java.oop.notify.Delivery;
import com.masternova.java.oop.notify.EmailChannel;
import com.masternova.java.oop.notify.FakeTransport;
import com.masternova.java.oop.notify.Notification;
import com.masternova.java.oop.notify.QuietHoursChannel;
import com.masternova.java.oop.notify.RetryingChannel;
import com.masternova.java.oop.notify.SmsChannel;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ChannelDecoratorTest {

  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  private static final Notification RECEIPT = new Notification("asha", "Your receipt", "Paid ₹1499");

  private static Clock at(String isoInstant) {
    return Clock.fixed(Instant.parse(isoInstant), IST);
  }

  private static Channel quiet(Channel inner, Clock clock) {
    return new QuietHoursChannel(inner, clock, IST, LocalTime.of(22, 0), LocalTime.of(8, 0));
  }

  @Test
  void retriesAnyChannelWithoutAChannelSpecificSubclass() {
    FakeTransport gateway = new FakeTransport();
    gateway.failNext(2);

    Delivery delivery = new RetryingChannel(new SmsChannel(gateway), 3).send(RECEIPT);

    assertThat(delivery).isEqualTo(new Delivery.Sent("sms", 3));
    assertThat(gateway.sent()).containsExactly("sms to asha: Your receipt");
  }

  @Test
  void givesUpWithAFailedResultNotAnException() {
    FakeTransport smtp = new FakeTransport();
    smtp.failNext(5);

    Delivery delivery = new RetryingChannel(new EmailChannel(smtp), 2).send(RECEIPT);

    assertThat(delivery).isInstanceOf(Delivery.Failed.class);
    assertThat(smtp.sent()).isEmpty();
  }

  @Test
  void quietHoursDeferUntilMorningAcrossMidnight() {
    FakeTransport smtp = new FakeTransport();
    // 23:30 IST = 18:00 UTC
    Delivery late = quiet(new EmailChannel(smtp), at("2026-10-02T18:00:00Z")).send(RECEIPT);
    // 02:00 IST = 20:30 UTC the previous day
    Delivery night = quiet(new EmailChannel(smtp), at("2026-10-02T20:30:00Z")).send(RECEIPT);

    assertThat(late).isEqualTo(new Delivery.Deferred(Instant.parse("2026-10-03T02:30:00Z"))); // 08:00 IST next day
    assertThat(night).isEqualTo(new Delivery.Deferred(Instant.parse("2026-10-03T02:30:00Z"))); // 08:00 IST same day
    assertThat(smtp.sent()).isEmpty();
  }

  @Test
  void decoratorsStackInAnyOrder() {
    FakeTransport smtp = new FakeTransport();
    smtp.failNext(1);
    // 14:00 IST = 08:30 UTC — daytime, so quiet hours let it through to the retrying email channel
    Channel channel = quiet(new RetryingChannel(new EmailChannel(smtp), 3), at("2026-10-02T08:30:00Z"));

    assertThat(channel.send(RECEIPT)).isEqualTo(new Delivery.Sent("email", 2));
    assertThat(channel.name()).isEqualTo("email");
  }
}
