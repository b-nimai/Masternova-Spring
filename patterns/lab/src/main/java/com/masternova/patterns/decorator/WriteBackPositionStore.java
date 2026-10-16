package com.masternova.patterns.decorator;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Decorator #2, write-back: {@link #record} only merges into a buffer; {@link #flush} hands the
 * whole buffer to the wrapped store in ONE write. Reads merge the buffer, so callers never see an
 * older position than they wrote. (The real one keeps the buffer in Redis and swaps it with {@code
 * RENAME}; this one is a map.)
 */
public final class WriteBackPositionStore implements PositionStore {

  private final PositionStore inner;
  private Map<String, Integer> dirty = new HashMap<>();

  public WriteBackPositionStore(PositionStore inner) {
    this.inner = inner;
  }

  @Override
  public void record(String key, int position) {
    dirty.merge(key, position, Math::max); // ⭐ the same merge as the store's: idempotent
  }

  @Override
  public void recordAll(Map<String, Integer> positions) {
    positions.forEach(this::record);
  }

  @Override
  public Optional<Integer> find(String key) {
    Optional<Integer> row = inner.find(key);
    Integer buffered = dirty.get(key);
    return buffered == null ? row : Optional.of(Math.max(buffered, row.orElse(0)));
  }

  /** Swap the buffer out first, so records during the write land in the next flush. */
  public void flush() {
    Map<String, Integer> batch = dirty;
    dirty = new HashMap<>();
    if (!batch.isEmpty()) {
      inner.recordAll(batch);
    }
  }
}
