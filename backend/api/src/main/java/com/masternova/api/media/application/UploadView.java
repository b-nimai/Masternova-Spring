package com.masternova.api.media.application;

import com.masternova.api.media.domain.PartPlan;
import com.masternova.api.media.domain.UploadStatus;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An upload as the uploader needs it: the plan, the parts still to send (with fresh presigned urls)
 * and how much the provider already holds.
 */
public record UploadView(
    UUID uploadId,
    UUID assetId,
    UploadStatus status,
    PartPlan plan,
    long uploadedBytes,
    List<PartUrl> missingParts,
    Instant expiresAt) {

  public record PartUrl(int number, URI url) {}
}
