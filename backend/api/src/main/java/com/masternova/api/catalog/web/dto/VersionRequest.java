package com.masternova.api.catalog.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Undo / redo bodies. ⭐ They carry {@code expectedVersion}, so they are VERSIONED writes: a stale
 * tab can't undo someone else's newer edit, and a double-tapped undo undoes once (the second press
 * gets 409) — no Idempotency-Key needed.
 */
public record VersionRequest(@NotNull @PositiveOrZero Long expectedVersion) {}
