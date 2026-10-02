package com.masternova.patterns.registry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * <b>Registry</b> — chooses an implementation by a key, with no {@code switch}.
 *
 * <p>⭐ Built ONCE from a collection (Spring: constructor-injected {@code List<Handler>}). Adding a
 * new message type means adding a new {@link Handler} class — this class never changes (Open/Closed).
 * Two handlers for one key are a wiring bug, so construction fails fast instead of silently letting
 * the last one win.
 */
public final class HandlerRegistry {

  /** The outcome of one dispatch: sealed, so callers switch over it exhaustively. */
  public sealed interface Dispatch {
    record Delivered(String type) implements Dispatch {}

    record NoHandler(String type) implements Dispatch {}
  }

  private final Map<String, Handler> byType;

  public HandlerRegistry(Collection<? extends Handler> handlers) {
    Map<String, Handler> index = new LinkedHashMap<>();
    for (Handler handler : handlers) {
      Handler previous = index.putIfAbsent(handler.type(), handler);
      if (previous != null) {
        throw new IllegalStateException("two handlers registered for " + handler.type());
      }
    }
    this.byType = Map.copyOf(index); // immutable after construction: safe to share across threads
  }

  public Optional<Handler> find(String type) {
    return Optional.ofNullable(byType.get(type));
  }

  public Dispatch dispatch(Message message) {
    return find(message.type())
        .<Dispatch>map(
            handler -> {
              handler.handle(message);
              return new Dispatch.Delivered(message.type());
            })
        .orElseGet(() -> new Dispatch.NoHandler(message.type()));
  }

  public Set<String> types() {
    return byType.keySet();
  }
}
