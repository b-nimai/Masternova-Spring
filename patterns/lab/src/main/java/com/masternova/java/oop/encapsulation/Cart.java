package com.masternova.java.oop.encapsulation;

import com.masternova.java.streams.Course;
import com.masternova.java.valueobject.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ⭐ Encapsulation as INVARIANTS, not getters/setters. A cart's rules live inside the cart:
 *
 * <ul>
 *   <li>only published courses, each at most once, at most {@value #MAX_ITEMS} items
 *   <li>all prices in one currency
 *   <li>the total is always right — it's computed, never stored or set from outside
 * </ul>
 *
 * <p>There is NO {@code setItems} and NO {@code getItems()} returning the live list: the only way
 * to change a cart is through methods that check the rules ("tell, don't ask"). This is a RICH
 * domain model; the opposite — a bag of getters/setters with the rules spread over services — is
 * the ANEMIC model.
 */
public final class Cart {

  public static final int MAX_ITEMS = 20;

  private final String ownerId;
  private final List<Course> items = new ArrayList<>(); // ⭐ private, mutable, never handed out

  public Cart(String ownerId) {
    this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
  }

  public void add(Course course) {
    Objects.requireNonNull(course, "course");
    if (!course.published()) {
      throw new IllegalStateException("course " + course.id() + " is not available");
    }
    if (contains(course.id())) {
      throw new IllegalStateException("course " + course.id() + " is already in the cart");
    }
    if (items.size() == MAX_ITEMS) {
      throw new IllegalStateException("a cart holds at most " + MAX_ITEMS + " courses");
    }
    if (!items.isEmpty() && !items.getFirst().price().currency().equals(course.price().currency())) {
      throw new IllegalStateException("all courses in a cart must share one currency");
    }
    items.add(course);
  }

  public boolean remove(String courseId) {
    return items.removeIf(c -> c.id().equals(courseId));
  }

  public boolean contains(String courseId) {
    return items.stream().anyMatch(c -> c.id().equals(courseId));
  }

  /** ⭐ Derived, never stored: it cannot drift out of sync with the items. */
  public Money total() {
    return items.stream()
        .map(Course::price)
        .reduce((a, b) -> a.plus(b))
        .orElseGet(() -> Money.zero("INR"));
  }

  /** ⭐ An unmodifiable snapshot — callers can read, never mutate behind the cart's back. */
  public List<Course> items() {
    return List.copyOf(items);
  }

  public String ownerId() {
    return ownerId;
  }
}
