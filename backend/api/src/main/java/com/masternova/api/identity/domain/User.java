package com.masternova.api.identity.domain;

import com.masternova.api.identity.Role;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The user AGGREGATE ROOT — a rich entity (note 06 §6): its rules live here, not in services. New
 * users are LEARNERs; a user always keeps at least one role; verification happens once.
 *
 * <p>JPA rules (note 10 §4): not final, protected no-arg constructor, {@code @Version}.
 */
@Entity
@Table(name = "app_user")
public class User {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 320)
  private String email; // stored normalised; exposed as the Email value object

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  // ⭐ EAGER on purpose: tiny, and needed on EVERY login to build the token. Fetching it lazily
  //    would add a query per login for no benefit (note 10 §6).
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "user_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private Set<Role> roles = EnumSet.noneOf(Role.class);

  @Column(name = "email_verified_at")
  private Instant emailVerifiedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Version private Long version; // null = new (Spring Data's isNew check), then 0, 1, 2…

  protected User() {} // for Hibernate

  private User(UUID id, Email email, String displayName, String passwordHash, Instant now) {
    this.id = id;
    this.email = email.value();
    this.displayName = displayName;
    this.passwordHash = passwordHash;
    this.roles.add(Role.LEARNER);
    this.createdAt = now;
  }

  /** ⭐ A named factory for the one legal way to create a user. */
  public static User register(Email email, String displayName, String passwordHash, Instant now) {
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(passwordHash, "passwordHash");
    String name = Objects.requireNonNull(displayName, "displayName").strip();
    if (name.isEmpty()) {
      throw new IllegalArgumentException("display name is required");
    }
    return new User(UUID.randomUUID(), email, name, passwordHash, now);
  }

  public void verifyEmail(Instant now) {
    if (emailVerifiedAt == null) { // idempotent: verifying twice keeps the first time
      emailVerifiedAt = now;
    }
  }

  public void grant(Role role) {
    roles.add(Objects.requireNonNull(role, "role"));
  }

  public void revoke(Role role) {
    if (roles.size() == 1 && roles.contains(role)) {
      throw new IllegalStateException("a user must keep at least one role");
    }
    roles.remove(role);
  }

  public UUID id() {
    return id;
  }

  public Email email() {
    return new Email(email);
  }

  public String displayName() {
    return displayName;
  }

  public String passwordHash() {
    return passwordHash;
  }

  /** A read-only copy — callers change roles only through grant/revoke. */
  public Set<Role> roles() {
    return Set.copyOf(roles);
  }

  public boolean isEmailVerified() {
    return emailVerifiedAt != null;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
