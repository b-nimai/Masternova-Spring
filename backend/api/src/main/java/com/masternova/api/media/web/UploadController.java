package com.masternova.api.media.web;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.media.application.UploadService;
import com.masternova.api.media.web.dto.CreateUploadRequest;
import com.masternova.api.media.web.dto.UploadResponse;
import com.masternova.api.platform.IdempotencyKeyRequired;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resumable uploads (media LLD §5). The bytes go browser → storage on the presigned urls these
 * responses carry; nothing here streams a file.
 */
@RestController
@RequestMapping("/api/v1/media/uploads")
class UploadController {

  private final UploadService uploads;

  UploadController(UploadService uploads) {
    this.uploads = uploads;
  }

  /** Unsafe, nothing exists yet: an Idempotency-Key makes a retried create return the same 201. */
  @PostMapping
  @IdempotencyKeyRequired
  ResponseEntity<UploadResponse> create(
      @Valid @RequestBody CreateUploadRequest request, CurrentUser me) {
    UploadResponse body =
        UploadResponse.from(
            uploads.create(me, request.filename(), request.contentType(), request.sizeBytes()));
    return ResponseEntity.created(URI.create("/api/v1/media/uploads/" + body.uploadId()))
        .body(body);
  }

  /** Progress AND resume: the parts the provider still lacks, with fresh urls. */
  @GetMapping("/{id}")
  UploadResponse get(@PathVariable UUID id, CurrentUser me) {
    return UploadResponse.from(uploads.resume(me, id));
  }

  /** No body: the server reads the part list from the provider (ADR-0014). Safe to retry. */
  @PostMapping("/{id}/complete")
  UploadResponse complete(@PathVariable UUID id, CurrentUser me) {
    return UploadResponse.from(uploads.complete(me, id));
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> abort(@PathVariable UUID id, CurrentUser me) {
    uploads.abort(me, id);
    return ResponseEntity.noContent().build();
  }
}
