package com.masternova.api.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Optional;
import java.util.UUID;

/**
 * A course category — REFERENCE DATA seeded by Flyway (V6), read-only to the application. Two
 * levels: a root has no parent, a child's parent is a root. Browsing a root includes its children.
 */
@Entity
@Table(name = "category")
public class Category {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 80)
  private String slug;

  @Column(nullable = false, length = 80)
  private String name;

  // ⭐ @ManyToOne is EAGER by default in JPA — a classic hidden N+1. Every to-one here is LAZY.
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private Category parent;

  @Column(nullable = false)
  private int position;

  protected Category() {} // for Hibernate

  public UUID id() {
    return id;
  }

  public String slug() {
    return slug;
  }

  public String name() {
    return name;
  }

  public Optional<Category> parent() {
    return Optional.ofNullable(parent);
  }

  public boolean isRoot() {
    return parent == null;
  }

  public int position() {
    return position;
  }
}
