package com.masternova.patterns.registry;

/**
 * <b>Product</b> of the registry: one handler per message type. In Masternova this is {@code
 * OutboxHandler}; Spring hands the relay every bean that implements it.
 */
public interface Handler {

  /** The key this handler is registered under. */
  String type();

  void handle(Message message);
}
