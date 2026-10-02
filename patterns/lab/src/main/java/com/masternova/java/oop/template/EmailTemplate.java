package com.masternova.java.oop.template;

/**
 * ✅ Inheritance used WELL — the <b>Template Method</b> pattern. The base class owns the fixed
 * skeleton of every Masternova email (greeting → body → footer → unsubscribe line) in a FINAL
 * method; subclasses fill in only the steps that vary. Phase 4 builds the real email templates
 * this way.
 *
 * <p>Why inheritance is OK here (unlike InstrumentedTagSet): the parent is DESIGNED for extension
 * — it documents exactly which methods subclasses provide (abstract) or may override (hooks), and
 * it never calls an overridable method in a way subclasses can't predict.
 */
public abstract class EmailTemplate {

  /**
   * ⭐ The template method: {@code final}, so no subclass can reorder the skeleton or skip the
   * unsubscribe line (a legal requirement for marketing email).
   */
  public final String render(String learnerName) {
    return "Hi " + learnerName + ",\n\n"
        + body() + "\n\n"
        + footer() + "\n"
        + "--\nUnsubscribe: https://masternova.dev/u/" + unsubscribeTopic();
  }

  /** ⭐ Abstract step: every email MUST provide its subject. */
  public abstract String subject();

  /** ⭐ Abstract step: the part that differs per email. */
  protected abstract String body();

  /** ⭐ Hook: a sensible default that subclasses MAY override. */
  protected String footer() {
    return "Happy learning,\nThe Masternova team";
  }

  protected abstract String unsubscribeTopic();
}
