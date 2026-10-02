package com.masternova.java.generics;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A generic, read-only "key → implementation" registry — the lookup at the heart of the Strategy
 * pattern ({@code patterns/docs/01-strategy.md}), written ONCE for any key and value type.
 *
 * <p>Masternova will use it for payment gateways (Phase 9), outbox event handlers (Phase 4) and
 * pipeline job processors (Phase 7).
 *
 * @param <K> the key type
 * @param <V> the value type — ⭐ BOUNDED: it must implement {@code Keyed<K>}, so the registry can
 *     ask each value for its own key. {@code Registry<String, Integer>} does not compile.
 */
public final class Registry<K, V extends Keyed<K>> {

  private final Map<K, V> byKey;

  private Registry(LinkedHashMap<K, V> byKey) {
    // ⭐ NOT Map.copyOf: its iteration order is unspecified (and varies between JVM runs), so
    //    keys() would come back shuffled. An unmodifiable VIEW over a private LinkedHashMap keeps
    //    registration order and still can't be modified (nobody else holds the LinkedHashMap).
    this.byKey = Collections.unmodifiableMap(byKey);
  }

  /**
   * ⭐ {@code Collection<? extends V>}: accepts a {@code List<RazorpayGateway>} as well as a
   * {@code List<PaymentGateway>} — the collection is only READ (a producer), so any subtype is
   * fine.
   */
  public static <K, V extends Keyed<K>> Registry<K, V> of(Collection<? extends V> values) {
    LinkedHashMap<K, V> byKey = new LinkedHashMap<>();
    for (V value : values) {
      V previous = byKey.putIfAbsent(value.key(), value);
      if (previous != null) {
        throw new IllegalArgumentException("duplicate key: " + value.key());
      }
    }
    return new Registry<>(byKey);
  }

  public Optional<V> find(K key) {
    return Optional.ofNullable(byKey.get(key));
  }

  public V get(K key) {
    return find(key).orElseThrow(() -> new IllegalArgumentException("nothing registered for " + key));
  }

  public Set<K> keys() {
    return byKey.keySet();
  }
}
