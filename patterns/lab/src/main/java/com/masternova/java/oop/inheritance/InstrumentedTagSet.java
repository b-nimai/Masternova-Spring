package com.masternova.java.oop.inheritance;

import java.util.Collection;
import java.util.HashSet;

/**
 * ❌ BROKEN ON PURPOSE — the "fragile base class" problem (Effective Java, item 18).
 *
 * <p>Goal: count how many tags were ever added. It extends {@link HashSet} and overrides both add
 * methods. But {@code HashSet.addAll} is implemented by calling {@code add} for each element — an
 * implementation detail of the PARENT — so every tag added through addAll is counted TWICE.
 *
 * <p>Study note: {@code patterns/java/06-oop-composition-over-inheritance.md} §2.
 */
public class InstrumentedTagSet extends HashSet<String> {

  private int addCount;

  @Override
  public boolean add(String tag) {
    addCount++;
    return super.add(tag);
  }

  @Override
  public boolean addAll(Collection<? extends String> tags) {
    addCount += tags.size();
    return super.addAll(tags); // ❌ calls this.add(...) for each tag → counted again
  }

  public int addCount() {
    return addCount;
  }
}
