package com.masternova.api.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * identity's PUBLIC module API for other modules (CLAUDE.md §3): the few facts about a user they
 * may ask for, without touching identity's tables or entities. Added in Phase 6, when catalog
 * needed an instructor's display name to snapshot onto a new course.
 */
public interface IdentityApi {

  /** The user's current display name, or empty if there's no such user. */
  Optional<String> displayName(UUID userId);
}
