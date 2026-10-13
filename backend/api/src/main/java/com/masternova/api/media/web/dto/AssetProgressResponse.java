package com.masternova.api.media.web.dto;

import com.masternova.api.media.application.AssetProgressView;
import java.util.List;
import java.util.UUID;

public record AssetProgressResponse(
    UUID assetId, String status, String failureCode, int percent, List<JobProgressResponse> jobs) {

  public record JobProgressResponse(String type, String rung, String status, int percent) {}

  public static AssetProgressResponse from(AssetProgressView v) {
    return new AssetProgressResponse(
        v.assetId(),
        v.status().name(),
        v.failureCode().orElse(null),
        v.percent(),
        v.jobs().stream()
            .map(j -> new JobProgressResponse(j.type(), j.rung(), j.status(), j.percent()))
            .toList());
  }
}
