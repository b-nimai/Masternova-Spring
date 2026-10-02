package com.masternova.worker;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** A clock tests can move forward (leases, staleness). */
public final class MutableClock extends Clock {

  public static final Instant T0 = Instant.parse("2026-10-02T10:00:00Z");

  private final AtomicReference<Instant> now = new AtomicReference<>(T0);

  public void advance(Duration duration) {
    now.updateAndGet(i -> i.plus(duration));
  }

  public void reset() {
    now.set(T0);
  }

  @Override
  public Instant instant() {
    return now.get();
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }
}
