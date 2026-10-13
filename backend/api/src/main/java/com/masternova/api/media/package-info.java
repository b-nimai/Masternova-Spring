/**
 * Media module — an instructor's video from the browser into storage (resumable, presigned
 * multipart, the provider as the source of truth) and, from 7.9, short-lived playback urls.
 * Transcoding is the worker's pipeline. Design: docs/lld/media.md.
 *
 * <p>Public API: {@link com.masternova.api.media.MediaApi} (catalog asks readiness and ownership)
 * and the {@link com.masternova.api.media.MediaUploaded} event (outbox → the worker's pipeline).
 */
package com.masternova.api.media;
