package com.masternova.java.generics;

import com.masternova.java.sealed.LectureContent;
import com.masternova.java.sealed.LectureContents;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/** Generic methods with bounds and wildcards — the PECS rule in practice (note §4–§6). */
public final class Ranking {

  private Ranking() {}

  /**
   * The largest element by natural order.
   *
   * <p>⭐ {@code <T extends Comparable<? super T>>} — the signature of {@code Collections.max}:
   *
   * <ul>
   *   <li>{@code T extends Comparable<…>}: an UPPER BOUND — T must be comparable, so {@code
   *       compareTo} can be called.
   *   <li>{@code Comparable<? super T>}: T may be comparable to one of its SUPERTYPES. {@code
   *       LocalDate} implements {@code Comparable<ChronoLocalDate>}, not {@code
   *       Comparable<LocalDate>} — with a plain {@code Comparable<T>} bound, {@code max} of a list
   *       of dates would not compile.
   *   <li>{@code Collection<? extends T>}: the collection is only read (a producer).
   * </ul>
   */
  public static <T extends Comparable<? super T>> T max(Collection<? extends T> items) {
    Iterator<? extends T> it = items.iterator();
    if (!it.hasNext()) {
      throw new NoSuchElementException("max of an empty collection");
    }
    T best = it.next();
    while (it.hasNext()) {
      T candidate = it.next();
      if (candidate.compareTo(best) > 0) {
        best = candidate;
      }
    }
    return best;
  }

  /**
   * ⭐ PECS in one signature: {@code from} PRODUCES T's ({@code ? extends T}), {@code to} CONSUMES
   * them ({@code ? super T}). Copies VideoContent from a {@code List<VideoContent>} into a {@code
   * List<LectureContent>} or even a {@code List<Object>}.
   */
  public static <T> void copyAll(Collection<? extends T> from, Collection<? super T> to) {
    for (T item : from) {
      to.add(item);
    }
  }

  /**
   * ⭐ Producer-only parameter: accepts {@code List<VideoContent>}, {@code List<QuizContent>} or
   * {@code List<LectureContent>}. A parameter typed {@code Collection<LectureContent>} would
   * reject {@code List<VideoContent>} — generics are INVARIANT (note §4).
   */
  public static int totalMinutes(Collection<? extends LectureContent> contents) {
    int total = 0;
    for (LectureContent content : contents) { // reading as the bound type is always safe
      total += LectureContents.estimatedMinutes(content);
    }
    // contents.add(...) would NOT compile: the exact element type is unknown.
    return total;
  }
}
