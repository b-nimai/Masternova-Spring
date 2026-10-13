package com.masternova.api.media.domain;

/**
 * An asset's lifecycle (media LLD §3). Linear, so an enum with one guard per step on {@link
 * MediaAsset} — not a State pattern: no event's ANSWER varies by status, only whether it's legal.
 * The worker moves {@code UPLOADED → PROCESSING → READY | FAILED} with conditional SQL updates.
 */
public enum MediaStatus {
  AWAITING_UPLOAD,
  UPLOADED,
  PROCESSING,
  READY,
  FAILED
}
