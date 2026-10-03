package com.masternova.api.catalog.web.dto;

import com.masternova.api.catalog.domain.PublishCheck;
import java.util.List;

/** The publish checklist: one row per requirement, and whether all of them pass. */
public record ReadinessResponse(boolean ready, List<Requirement> requirements) {

  public record Requirement(String code, String message, boolean satisfied) {}

  public static ReadinessResponse from(List<PublishCheck> checks) {
    return new ReadinessResponse(
        checks.stream().allMatch(PublishCheck::satisfied),
        checks.stream().map(c -> new Requirement(c.code(), c.message(), c.satisfied())).toList());
  }
}
