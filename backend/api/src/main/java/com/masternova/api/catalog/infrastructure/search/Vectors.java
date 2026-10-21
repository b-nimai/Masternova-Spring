package com.masternova.api.catalog.infrastructure.search;

/**
 * pgvector's text form, {@code [0.1,0.2,…]}: sent as a string parameter and cast with {@code
 * ::vector}, so no driver type is needed.
 */
final class Vectors {

  private Vectors() {}

  static String literal(float[] vector) {
    StringBuilder text = new StringBuilder(vector.length * 10).append('[');
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        text.append(',');
      }
      text.append(vector[i]);
    }
    return text.append(']').toString();
  }
}
