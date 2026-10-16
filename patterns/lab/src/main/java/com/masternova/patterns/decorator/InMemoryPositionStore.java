package com.masternova.patterns.decorator;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** ConcreteComponent: the "database". Counts its writes, which the buffer exists to save. */
public final class InMemoryPositionStore implements PositionStore {

  private final Map<String, Integer> rows = new HashMap<>();
  private int writes;

  @Override
  public void record(String key, int position) {
    recordAll(Map.of(key, position));
  }

  @Override
  public void recordAll(Map<String, Integer> positions) {
    writes++; // one transaction, however many rows
    positions.forEach((key, position) -> rows.merge(key, position, Math::max));
  }

  @Override
  public Optional<Integer> find(String key) {
    return Optional.ofNullable(rows.get(key));
  }

  public int writes() {
    return writes;
  }
}
