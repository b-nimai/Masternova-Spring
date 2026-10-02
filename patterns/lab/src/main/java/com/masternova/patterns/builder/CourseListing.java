package com.masternova.patterns.builder;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An IMMUTABLE object with two required and many optional fields, built with Bloch's BUILDER
 * (Effective Java item 2). Compare the alternatives:
 *
 * <ul>
 *   <li>telescoping constructors: {@code new CourseListing("K8s", 149900, null, "en", null, true,
 *       null)} — which {@code null} is which?
 *   <li>JavaBeans setters: readable, but the object is MUTABLE and can be half-built
 *       (inconsistent) between the setter calls.
 *   <li>a builder: named steps, one {@link Builder#build()} that validates the whole thing, and
 *       an immutable result.
 * </ul>
 */
public final class CourseListing {

  private final String title;
  private final long priceMinor;
  private final String subtitle;
  private final String language;
  private final Set<String> tags;
  private final boolean featured;

  private CourseListing(Builder b) {
    this.title = b.title;
    this.priceMinor = b.priceMinor;
    this.subtitle = b.subtitle;
    this.language = b.language;
    this.tags = Set.copyOf(b.tags); // ⭐ defensive copy: the builder can be reused afterwards
    this.featured = b.featured;
  }

  /** ⭐ Required fields go in the builder's entry point, so they can't be forgotten. */
  public static Builder builder(String title, long priceMinor) {
    return new Builder(title, priceMinor);
  }

  /** A builder pre-filled from this object: "the same, but…" without a setter in sight. */
  public Builder toBuilder() {
    return new Builder(title, priceMinor)
        .subtitle(subtitle)
        .language(language)
        .tags(List.copyOf(tags))
        .featured(featured);
  }

  public String title() {
    return title;
  }

  public long priceMinor() {
    return priceMinor;
  }

  public String subtitle() {
    return subtitle;
  }

  public String language() {
    return language;
  }

  public Set<String> tags() {
    return tags;
  }

  public boolean featured() {
    return featured;
  }

  /** The builder: mutable, short-lived, one per object being built. */
  public static final class Builder {
    private final String title;
    private final long priceMinor;
    private String subtitle;
    private String language = "en"; // ⭐ defaults live in ONE place
    private List<String> tags = List.of();
    private boolean featured;

    private Builder(String title, long priceMinor) {
      this.title = title;
      this.priceMinor = priceMinor;
    }

    public Builder subtitle(String subtitle) {
      this.subtitle = subtitle;
      return this; // ⭐ return this: the calls chain
    }

    public Builder language(String language) {
      this.language = language;
      return this;
    }

    public Builder tags(List<String> tags) {
      this.tags = tags;
      return this;
    }

    public Builder featured(boolean featured) {
      this.featured = featured;
      return this;
    }

    /** ⭐ Validation of the WHOLE object, once — rules that span fields can only live here. */
    public CourseListing build() {
      Objects.requireNonNull(title, "title");
      if (title.isBlank()) {
        throw new IllegalArgumentException("title is required");
      }
      if (priceMinor < 0) {
        throw new IllegalArgumentException("price cannot be negative");
      }
      if (featured && priceMinor == 0) {
        throw new IllegalArgumentException("free courses can't be featured"); // a cross-field rule
      }
      return new CourseListing(this);
    }
  }
}
