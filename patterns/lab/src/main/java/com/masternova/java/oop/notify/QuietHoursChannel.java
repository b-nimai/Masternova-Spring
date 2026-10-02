package com.masternova.java.oop.notify;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * ⭐ DECORATOR: don't wake learners up. Between {@code start} and {@code end} (e.g. 22:00–08:00)
 * nothing is sent; the notification is deferred until the window ends.
 *
 * <p>Takes a {@link Clock} (note 01's rule: inject time) so tests can pick the "current" time.
 */
public final class QuietHoursChannel implements Channel {

  private final Channel inner;
  private final Clock clock;
  private final ZoneId zone;
  private final LocalTime start;
  private final LocalTime end;

  public QuietHoursChannel(Channel inner, Clock clock, ZoneId zone, LocalTime start, LocalTime end) {
    this.inner = Objects.requireNonNull(inner, "inner");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.zone = Objects.requireNonNull(zone, "zone");
    this.start = Objects.requireNonNull(start, "start");
    this.end = Objects.requireNonNull(end, "end");
  }

  @Override
  public Delivery send(Notification notification) {
    ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
    if (isQuiet(now.toLocalTime())) {
      ZonedDateTime until = now.toLocalTime().isBefore(end)
          ? now.with(end) //                       after midnight: later today
          : now.plusDays(1).with(end); //          before midnight: tomorrow morning
      return new Delivery.Deferred(until.toInstant());
    }
    return inner.send(notification);
  }

  private boolean isQuiet(LocalTime time) {
    // the window wraps midnight when start > end (22:00 → 08:00)
    return start.isAfter(end)
        ? !time.isBefore(start) || time.isBefore(end)
        : !time.isBefore(start) && time.isBefore(end);
  }

  @Override
  public String name() {
    return inner.name();
  }
}
