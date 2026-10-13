package com.masternova.api.media.web.dto;

import com.masternova.api.media.application.UploadView;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The uploader's whole working state (API conventions §8): the plan, what the provider already
 * holds, and a presigned url per part still to send. Same shape for create, resume and complete.
 */
public record UploadResponse(
    UUID uploadId,
    UUID assetId,
    String status,
    long sizeBytes,
    long partSizeBytes,
    int partCount,
    long uploadedBytes,
    List<PartUrlResponse> parts,
    Instant expiresAt) {

  public record PartUrlResponse(int number, URI url) {}

  public static UploadResponse from(UploadView v) {
    return new UploadResponse(
        v.uploadId(),
        v.assetId(),
        v.status().name(),
        v.plan().size(),
        v.plan().partSize(),
        v.plan().partCount(),
        v.uploadedBytes(),
        v.missingParts().stream().map(p -> new PartUrlResponse(p.number(), p.url())).toList(),
        v.expiresAt());
  }
}
