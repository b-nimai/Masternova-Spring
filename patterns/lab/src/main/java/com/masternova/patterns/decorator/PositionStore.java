package com.masternova.patterns.decorator;

import java.util.Map;
import java.util.Optional;

/**
 * Component of the second decorator (Phase 10): how far each learner got in each lecture. The
 * merge is "max only goes up", so writing the same position twice, or late, changes nothing.
 */
public interface PositionStore {

  void record(String key, int position);

  void recordAll(Map<String, Integer> positions);

  Optional<Integer> find(String key);
}
