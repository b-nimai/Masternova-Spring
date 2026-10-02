package com.masternova.java.oop.dispatch;

/**
 * Which method actually runs? Java's rules, as runnable code (all asserted in DispatchTrapsTest).
 *
 * <ul>
 *   <li>OVERRIDING (instance methods) — chosen at RUNTIME by the object's actual class.
 *   <li>OVERLOADING — chosen at COMPILE time by the variable's DECLARED type.
 *   <li>static methods and fields — NOT polymorphic: chosen by the declared type ("hiding").
 * </ul>
 */
public final class DispatchTraps {

  private DispatchTraps() {}

  public static class Lecture {
    public String label = "lecture"; // a FIELD — fields are never polymorphic

    public String kind() { // an instance method — overridable
      return "lecture";
    }

    public static String category() { // a static method — can only be HIDDEN, not overridden
      return "content";
    }
  }

  public static class VideoLecture extends Lecture {
    public String label = "video"; // ⚠️ HIDES Lecture.label — two separate fields now exist

    @Override
    public String kind() {
      return "video";
    }

    public static String category() { // ⚠️ hides Lecture.category(); no @Override possible
      return "media";
    }
  }

  // ---- overloads: same name, different parameter types
  public static String describe(Lecture lecture) {
    return "describe(Lecture)";
  }

  public static String describe(VideoLecture video) {
    return "describe(VideoLecture)";
  }

  // ---- interfaces with clashing default methods: the "diamond"
  public interface Purchasable {
    default String label() {
      return "purchasable";
    }
  }

  public interface Shareable {
    default String label() {
      return "shareable";
    }
  }

  /**
   * ⭐ Two interfaces supply the same default method → the class MUST override it (compile error
   * otherwise), and can pick one with {@code Interface.super.method()}.
   */
  public static final class PaidCourse implements Purchasable, Shareable {
    @Override
    public String label() {
      return Purchasable.super.label() + "+" + Shareable.super.label();
    }
  }
}
