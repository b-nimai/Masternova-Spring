package com.masternova.java.oop.notify;

/** A concrete channel: SMS (subject only — SMS is short). */
public final class SmsChannel implements Channel {

  private final FakeTransport gateway;

  public SmsChannel(FakeTransport gateway) {
    this.gateway = gateway;
  }

  @Override
  public Delivery send(Notification n) {
    gateway.deliver("sms to " + n.recipient() + ": " + n.subject());
    return new Delivery.Sent(name(), 1);
  }

  @Override
  public String name() {
    return "sms";
  }
}
