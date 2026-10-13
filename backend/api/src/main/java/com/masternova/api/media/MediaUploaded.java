package com.masternova.api.media;

import com.masternova.kernel.event.DomainEvent;

/**
 * A source video is complete in storage. Written to the outbox in the SAME transaction that marks
 * the asset {@code UPLOADED}; the worker's pipeline consumes it (video-pipeline LLD §5).
 */
public record MediaUploaded(String assetId, String ownerId, String sourceKey, long sizeBytes)
    implements DomainEvent {

  public static final String TYPE = "media.uploaded.v1";

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public String aggregateId() {
    return assetId;
  }
}
