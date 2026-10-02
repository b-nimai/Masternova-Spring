package com.masternova.patterns.outbox;

import java.util.ArrayList;
import java.util.List;

/** Stand-in for the email service / message broker / search index — can be "down". */
public final class Broker {

  private final List<String> delivered = new ArrayList<>();
  private boolean down;

  public void setDown(boolean down) {
    this.down = down;
  }

  public void publish(String event) {
    if (down) {
      throw new IllegalStateException("broker unavailable");
    }
    delivered.add(event);
  }

  public List<String> delivered() {
    return List.copyOf(delivered);
  }
}
