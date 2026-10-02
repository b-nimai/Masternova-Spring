package com.masternova.java.oop.notify;

/**
 * ⭐ The ONE abstraction every piece below depends on (Dependency Inversion): a way to deliver a
 * notification. Concrete channels (email, SMS) implement it; decorators (retry, quiet hours) also
 * implement it AND wrap another Channel — so features stack in any combination.
 */
public interface Channel {

  Delivery send(Notification notification);

  /** For logs and the Sent result. */
  String name();
}
