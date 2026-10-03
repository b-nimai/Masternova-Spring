package com.masternova.api.catalog.domain;

import java.util.UUID;

/**
 * A lecture's state, captured before it's removed: a MEMENTO. Restoring it brings the lecture back
 * with the SAME id — media (Phase 7) and progress (Phase 10) refer to lectures by id.
 */
public record LectureSnapshot(
    UUID id, String title, LectureKind kind, boolean preview, int durationSeconds, UUID assetId) {}
