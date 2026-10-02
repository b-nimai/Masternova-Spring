package com.masternova.java.oop.inheritance;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * ✅ The same feature with COMPOSITION: wrap a Set instead of extending one ("has-a", not
 * "is-a"). Each method forwards to the inner set, so the parent's internals can never call back
 * into our overrides. Works with ANY Set implementation, too.
 */
public final class CountingTagSet {

  // ⭐ the wrapped object — private, chosen by us, never exposed
  private final Set<String> tags = new HashSet<>();
  private int addCount;

  public boolean add(String tag) {
    addCount++;
    return tags.add(tag);
  }

  public boolean addAll(Collection<String> newTags) {
    addCount += newTags.size();
    return tags.addAll(newTags); // the inner HashSet calls ITS add — not ours
  }

  public boolean contains(String tag) {
    return tags.contains(tag);
  }

  public int size() {
    return tags.size();
  }

  public int addCount() {
    return addCount;
  }

  /** ⭐ A read-only view — callers can't bypass add() and break the count. */
  public Set<String> view() {
    return Set.copyOf(tags);
  }
}
