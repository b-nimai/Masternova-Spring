package com.masternova.patterns.prototype;

import java.util.UUID;

/**
 * An IMMUTABLE lecture (a record): copies may share it freely — nothing can change it. {@code
 * assetId} stands for gigabytes of video that a copy must point at, never duplicate.
 */
public record Lecture(String title, int seconds, UUID assetId) {}
