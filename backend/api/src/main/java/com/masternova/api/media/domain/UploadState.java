package com.masternova.api.media.domain;

import com.masternova.api.platform.ConflictException;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.Map;

/**
 * An upload session's lifecycle as the STATE pattern (media LLD §3):
 *
 * <pre>
 *   OPEN ──complete──► COMPLETED      (complete again → the same 200)
 *     ├───abort──────► ABORTED        (abort again → 204)
 *     └─(past expiry)─► EXPIRED     (the worker's sweeper records it; abort → 204)
 * </pre>
 *
 * <ul>
 *   <li>⭐ Each event answers {@link Step#PERFORM} (do the work), {@link Step#REPLAY} (it's already
 *       done: answer as if it just happened — retries, double clicks, the sweeper racing the user)
 *       or throws 409 with a code that names the CURRENT state. Defaults throw; each state
 *       overrides only what it accepts, so the overrides are the table in the LLD.
 *   <li>Same shape as {@code CourseState} (catalog row 2): sealed records, exhaustive {@link #of}.
 * </ul>
 */
@DesignPattern(value = Pattern.STATE, role = "State (sealed)", note = "patterns/docs/02-state.md")
public sealed interface UploadState {

  /** What the caller must do for an event that the state accepts. */
  enum Step {
    /** Do the work and move to the next status. */
    PERFORM,
    /** Already done: change nothing, answer the same as the first time. */
    REPLAY
  }

  UploadStatus status();

  /** Hand out urls for the missing parts. */
  default Step resume() {
    throw rejected("resume");
  }

  default Step complete() {
    throw rejected("complete");
  }

  default Step abort() {
    throw rejected("abort");
  }

  private ConflictException rejected(String event) {
    String code =
        switch (status()) {
          case OPEN -> throw new IllegalStateException("OPEN accepts every event");
          case COMPLETED -> "UPLOAD_ALREADY_COMPLETED";
          case ABORTED -> "UPLOAD_ABORTED";
          case EXPIRED -> "UPLOAD_EXPIRED";
        };
    return new ConflictException(
        code,
        "Can't " + event + " an upload that is " + status().name().toLowerCase() + ".",
        Map.of("status", status().name(), "action", event.toUpperCase()));
  }

  // ------------------------------------------------------------------ the states

  record Open() implements UploadState {
    @Override
    public UploadStatus status() {
      return UploadStatus.OPEN;
    }

    @Override
    public Step resume() {
      return Step.PERFORM;
    }

    @Override
    public Step complete() {
      return Step.PERFORM;
    }

    @Override
    public Step abort() {
      return Step.PERFORM;
    }
  }

  record Completed() implements UploadState {
    @Override
    public UploadStatus status() {
      return UploadStatus.COMPLETED;
    }

    @Override
    public Step complete() {
      return Step.REPLAY; // ⭐ a retried complete gets the same 200, not a 409
    }
  }

  record Aborted() implements UploadState {
    @Override
    public UploadStatus status() {
      return UploadStatus.ABORTED;
    }

    @Override
    public Step abort() {
      return Step.REPLAY;
    }
  }

  record Expired() implements UploadState {
    @Override
    public UploadStatus status() {
      return UploadStatus.EXPIRED;
    }

    @Override
    public Step abort() {
      return Step.REPLAY; // the goal ("this upload is gone") already holds
    }
  }

  static UploadState of(UploadStatus status) {
    return switch (status) {
      case OPEN -> new Open();
      case COMPLETED -> new Completed();
      case ABORTED -> new Aborted();
      case EXPIRED -> new Expired();
    };
  }
}
