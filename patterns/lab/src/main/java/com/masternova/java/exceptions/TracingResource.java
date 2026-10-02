package com.masternova.java.exceptions;

import java.util.List;

/**
 * A resource that records when it is used and closed, and can fail in either — used by the tests
 * to show exactly what try-with-resources does (close order, suppressed exceptions).
 */
public final class TracingResource implements AutoCloseable {

  private final String name;
  private final List<String> log;
  private final boolean failOnClose;

  public TracingResource(String name, List<String> log, boolean failOnClose) {
    this.name = name;
    this.log = log;
    this.failOnClose = failOnClose;
    log.add("open " + name);
  }

  public void use(boolean fail) {
    log.add("use " + name);
    if (fail) {
      throw new IllegalStateException(name + " failed while in use");
    }
  }

  // ⭐ AutoCloseable.close() — called automatically at the end of a try-with-resources block.
  @Override
  public void close() {
    log.add("close " + name);
    if (failOnClose) {
      throw new IllegalStateException(name + " failed to close");
    }
  }
}
