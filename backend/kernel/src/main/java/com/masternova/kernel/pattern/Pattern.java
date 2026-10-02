package com.masternova.kernel.pattern;

/** The GoF and enterprise patterns this codebase uses, grouped by intent. */
public enum Pattern {
  // Creational
  BUILDER(Category.CREATIONAL),
  FACTORY_METHOD(Category.CREATIONAL),
  PROTOTYPE(Category.CREATIONAL),

  // Structural
  ADAPTER(Category.STRUCTURAL),
  DECORATOR(Category.STRUCTURAL),
  FACADE(Category.STRUCTURAL),
  PROXY(Category.STRUCTURAL),
  COMPOSITE(Category.STRUCTURAL),

  // Behavioral
  CHAIN_OF_RESPONSIBILITY(Category.BEHAVIORAL),
  COMMAND(Category.BEHAVIORAL),
  MEMENTO(Category.BEHAVIORAL),
  OBSERVER(Category.BEHAVIORAL),
  STATE(Category.BEHAVIORAL),
  STRATEGY(Category.BEHAVIORAL),
  TEMPLATE_METHOD(Category.BEHAVIORAL),

  // Enterprise / DDD
  REPOSITORY(Category.ENTERPRISE),
  SPECIFICATION(Category.ENTERPRISE),
  UNIT_OF_WORK(Category.ENTERPRISE),
  TRANSACTIONAL_OUTBOX(Category.ENTERPRISE);

  private final Category category;

  Pattern(Category category) {
    this.category = category;
  }

  public Category category() {
    return category;
  }

  public enum Category {
    CREATIONAL,
    STRUCTURAL,
    BEHAVIORAL,
    ENTERPRISE
  }
}
