package com.masternova.java.concurrency;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A live cohort with a fixed number of seats. The classic CHECK-THEN-ACT race: "is there a seat?"
 * and "take the seat" must happen as ONE atomic step, or two learners get the last seat.
 */
public final class Cohort {

  private final int capacity;
  private final AtomicInteger taken = new AtomicInteger();
  private int takenUnsafe; // only touched by tryEnrollUnsafe

  public Cohort(int capacity) {
    this.capacity = capacity;
  }

  /** ❌ BROKEN: between the check and the increment another thread can take the seat. */
  public boolean tryEnrollUnsafe() {
    if (takenUnsafe < capacity) { //  check …
      Thread.yield(); //                (widen the race window so the bug shows up reliably)
      takenUnsafe++; //                 … then act — not atomic
      return true;
    }
    return false;
  }

  /**
   * ✅ Lock-free: a CAS loop. Read the current value, compute the next one, and only write if
   * nobody changed it in between — otherwise retry. {@code updateAndGet} would hide the loop; it's
   * written out here because this loop IS the idea behind every atomic class.
   */
  public boolean tryEnroll() {
    while (true) {
      int current = taken.get();
      if (current >= capacity) {
        return false;
      }
      if (taken.compareAndSet(current, current + 1)) { // ⭐ atomic "if still current, set next"
        return true;
      }
      // someone else won the race for this value — loop and re-check
    }
  }

  public int seatsTaken() {
    return taken.get();
  }

  public int seatsTakenUnsafe() {
    return takenUnsafe;
  }
}
