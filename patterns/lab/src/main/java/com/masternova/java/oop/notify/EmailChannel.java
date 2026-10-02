package com.masternova.java.oop.notify;

/** A concrete channel: email. Knows nothing about retries or quiet hours — single responsibility. */
public final class EmailChannel implements Channel {

  private final FakeTransport smtp;

  public EmailChannel(FakeTransport smtp) {
    this.smtp = smtp;
  }

  @Override
  public Delivery send(Notification n) {
    smtp.deliver("email to " + n.recipient() + ": " + n.subject());
    return new Delivery.Sent(name(), 1);
  }

  @Override
  public String name() {
    return "email";
  }
}
