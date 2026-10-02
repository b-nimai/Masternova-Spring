package com.masternova.patterns.observer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * <b>Subject</b> — keeps a list of observers and notifies the interested ones when something is
 * published. Publishers never know who listens (or whether anyone does).
 *
 * <p>The Spring-free core of {@code ApplicationEventPublisher} + {@code @EventListener}.
 */
public final class EventBus {

  /** One registration: which event type, and who to call. */
  private record Subscriber<E>(Class<E> type, Consumer<? super E> observer) {
    void deliver(Object event) {
      observer.accept(type.cast(event)); // ⭐ type token (note 04 §9): a checked cast, no warning
    }
  }

  /** What happened during one publish — failures are REPORTED, not thrown. */
  public record PublishResult(int delivered, List<RuntimeException> failures) {}

  /** Returned by subscribe; call cancel() to stop observing. */
  @FunctionalInterface
  public interface Subscription {
    void cancel();
  }

  // ⭐ CopyOnWriteArrayList: observers may (un)subscribe while an event is being delivered,
  //    and iteration never throws ConcurrentModificationException (note 03 §4).
  private final List<Subscriber<?>> subscribers = new CopyOnWriteArrayList<>();

  /** Observe events of {@code type} — and of every subtype. */
  public <E> Subscription subscribe(Class<E> type, Consumer<? super E> observer) {
    Subscriber<E> subscriber = new Subscriber<>(Objects.requireNonNull(type), Objects.requireNonNull(observer));
    subscribers.add(subscriber);
    return () -> subscribers.remove(subscriber);
  }

  public PublishResult publish(Object event) {
    Objects.requireNonNull(event, "event");
    int delivered = 0;
    List<RuntimeException> failures = new ArrayList<>();
    for (Subscriber<?> subscriber : subscribers) {
      if (!subscriber.type().isInstance(event)) {
        continue; // not interested in this kind of event
      }
      try {
        subscriber.deliver(event);
        delivered++;
      } catch (RuntimeException e) {
        // ⭐ ISOLATION: one broken observer must not stop the others from being notified
        failures.add(e);
      }
    }
    return new PublishResult(delivered, List.copyOf(failures));
  }
}
