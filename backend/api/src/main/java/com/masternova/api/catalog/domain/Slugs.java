package com.masternova.api.catalog.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * URL slugs for courses: {@code "Kubernetes: From Zero!"} → {@code kubernetes-from-zero-x7k2q9}.
 * The random suffix makes two courses with the same title distinct without a lookup; the slug's
 * UNIQUE constraint still guards the 1-in-2-billion collision. A slug is set ONCE: renaming a
 * course never changes its URL (catalog.md §3).
 */
public final class Slugs {

  private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
  private static final int MAX = 140;
  private static final int SUFFIX = 7; // "-" + 6 characters

  private Slugs() {}

  public static String forTitle(String title) {
    String base =
        Normalizer.normalize(title, Normalizer.Form.NFD) // "é" → "e" + accent…
            .replaceAll("\\p{M}", "") // …drop the accent
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-");
    base = trimDashes(base);
    return withSuffix(base.isEmpty() ? "course" : base);
  }

  /** {@code kubernetes-basics} → {@code kubernetes-basics-copy-x7k2q9} (never …-copy-…-copy-…). */
  public static String forCopyOf(String slug) {
    return withSuffix(slug.replaceAll("(-copy-[a-z0-9]{6})+$", "") + "-copy");
  }

  private static String withSuffix(String base) {
    StringBuilder suffix = new StringBuilder("-");
    for (int i = 0; i < SUFFIX - 1; i++) {
      suffix.append(ALPHABET.charAt(ThreadLocalRandom.current().nextInt(ALPHABET.length())));
    }
    int room = MAX - SUFFIX;
    String trimmed = base.length() > room ? trimDashes(base.substring(0, room)) : base;
    return trimmed + suffix;
  }

  /**
   * Strips leading and trailing dashes with a linear scan. ⭐ Not {@code replaceAll("-+$", "")}: an
   * anchored {@code -+$} on user input backtracks from every dash (polynomial ReDoS, flagged by
   * CodeQL), so a title of many dashes would burn CPU. Two index walks are O(n).
   */
  static String trimDashes(String text) {
    int start = 0;
    int end = text.length();
    while (start < end && text.charAt(start) == '-') {
      start++;
    }
    while (end > start && text.charAt(end - 1) == '-') {
      end--;
    }
    return text.substring(start, end);
  }
}
