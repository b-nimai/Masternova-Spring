package com.masternova.api.media.web;

import com.masternova.api.media.application.DeadJobService;
import com.masternova.api.media.web.dto.DeadJobResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The transcode pipeline's dead-letter queue. ADMIN only (enforced on the service). */
@RestController
@RequestMapping("/api/v1/admin/media/jobs")
class AdminPipelineController {

  private final DeadJobService deadJobs;

  AdminPipelineController(DeadJobService deadJobs) {
    this.deadJobs = deadJobs;
  }

  @GetMapping("/dead")
  List<DeadJobResponse> dead() {
    return deadJobs.list().stream().map(DeadJobResponse::from).toList();
  }

  /**
   * Safe to retry: a second replay of the same job is 409 {@code JOB_NOT_DEAD}, changing nothing.
   */
  @PostMapping("/{id}/replay")
  DeadJobResponse replay(@PathVariable UUID id) {
    return DeadJobResponse.from(deadJobs.replay(id));
  }
}
