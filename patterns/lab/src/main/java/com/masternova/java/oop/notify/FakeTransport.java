package com.masternova.java.oop.notify;

import java.util.ArrayList;
import java.util.List;

/**
 * Stand-in for an SMTP server / SMS gateway: records what was sent, and can be told to fail the
 * next N calls (to exercise retries).
 */
public final class FakeTransport {

  private final List<String> sent = new ArrayList<>();
  private int failuresRemaining;

  public void failNext(int times) {
    failuresRemaining = times;
  }

  void deliver(String line) {
    if (failuresRemaining > 0) {
      failuresRemaining--;
      throw new IllegalStateException("transport unavailable");
    }
    sent.add(line);
  }

  public List<String> sent() {
    return List.copyOf(sent);
  }
}
