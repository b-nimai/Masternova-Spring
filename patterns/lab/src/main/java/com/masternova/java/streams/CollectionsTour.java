package com.masternova.java.streams;

import com.masternova.java.valueobject.Money;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * The Collections API without streams: the Map methods, NavigableMap, removeIf, and
 * SequencedCollection — things you'll use every day in services.
 *
 * <p>Study note: {@code patterns/java/03-collections-and-streams.md} §2–§3.
 */
public final class CollectionsTour {

  private CollectionsTour() {}

  /** Word → how often it appears (lower-cased, sorted). */
  public static Map<String, Integer> wordFrequency(String text) {
    Map<String, Integer> counts = new TreeMap<>();
    for (String word : text.toLowerCase().split("\\W+")) {
      if (!word.isEmpty()) {
        // ⭐ merge: 1 if absent, otherwise old + 1. Replaces the get / null-check / put dance.
        counts.merge(word, 1, Integer::sum);
      }
    }
    return counts;
  }

  /** Tag → courses that use it: the "multimap" idiom. */
  public static Map<String, List<String>> coursesByTag(List<Course> courses) {
    Map<String, List<String>> byTag = new TreeMap<>();
    for (Course course : courses) {
      for (String tag : course.tags()) {
        // ⭐ computeIfAbsent: create the list on first use, then add to it — one line.
        byTag.computeIfAbsent(tag, t -> new ArrayList<>()).add(course.id());
      }
    }
    byTag.values().forEach(ids -> ids.sort(Comparator.naturalOrder()));
    return byTag;
  }

  /** Removes unpublished courses from a MUTABLE list, in place. */
  public static void removeDrafts(List<Course> mutableCourses) {
    // ⭐ removeIf: the safe way to remove while iterating. Calling list.remove(...) inside a
    //    for-each loop throws ConcurrentModificationException (see the test).
    mutableCourses.removeIf(course -> !course.published());
  }

  /** Price → tier name, using a NavigableMap of thresholds. */
  public static String priceTier(Money price) {
    // ⭐ floorEntry(key) = the entry with the greatest key <= the given key. A TreeMap of
    //    thresholds turns "which band does this value fall in?" into one lookup — no if/else
    //    ladder. Keys are Money: TreeMap orders them with Money.compareTo (note 01).
    NavigableMap<Money, String> tiers = new TreeMap<>();
    tiers.put(Money.zero("INR"), "Free");
    tiers.put(Money.of(1, "INR"), "Budget");
    tiers.put(Money.of(1_000_00, "INR"), "Standard");
    tiers.put(Money.of(2_000_00, "INR"), "Premium");
    return tiers.floorEntry(price).getValue();
  }

  /** Newest and oldest published course, using Java 21's SequencedCollection methods. */
  public static List<String> newestAndOldest(List<Course> courses) {
    List<Course> byDate =
        courses.stream()
            .filter(Course::published)
            .sorted(Comparator.comparing(Course::publishedOn))
            .toList();
    // ⭐ getFirst()/getLast()/reversed() — Java 21 SequencedCollection. Before: list.get(0) and
    //    list.get(list.size() - 1).
    return List.of(byDate.getLast().title(), byDate.getFirst().title());
  }
}
