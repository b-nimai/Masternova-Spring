package com.masternova.java.concurrency;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Payment providers retry webhooks and may deliver the SAME event many times, concurrently. Each
 * event id must cause exactly one enrollment — the in-memory version of Phase 9's "claim before
 * process" (there, a database unique constraint plays the role of this set).
 */
public final class WebhookProcessor {

  // ⭐ A concurrent Set: add() is atomic. Only ONE thread gets `true` for a given id.
  private final Set<String> processed = ConcurrentHashMap.newKeySet();
  private final AtomicInteger enrollments = new AtomicInteger();

  /** @return true if this call did the work, false if the event was already handled */
  public boolean handle(String eventId) {
    if (!processed.add(eventId)) { // ⭐ claim first; losers stop here
      return false;
    }
    enrollments.incrementAndGet(); // the side effect — happens once per event id
    return true;
  }

  public int enrollments() {
    return enrollments.get();
  }
}
