package com.masternova.patterns.state;

/**
 * The second State in the project, with a twist the course lifecycle doesn't have: an event can be
 * accepted WITHOUT changing anything. A retried "complete" on a completed upload must answer the
 * same 200 as the first one, not a 409 — retries, double clicks and a sweeper racing the user all
 * hit it. So each event answers PERFORM / REPLAY / throws. Production: {@code
 * com.masternova.api.media.domain.UploadState}.
 */
public final class UploadLifecycle {

  private UploadLifecycle() {}

  public enum Step {
    PERFORM,
    REPLAY
  }

  public static final class Rejected extends RuntimeException {
    public final String code;

    Rejected(String code) {
      super(code);
      this.code = code;
    }
  }

  /** ⭐ Defaults reject with a code naming the CURRENT state; states override what they accept. */
  public sealed interface State {
    String code();

    default Step complete() {
      throw new Rejected(code());
    }

    default Step abort() {
      throw new Rejected(code());
    }
  }

  public record Open() implements State {
    @Override
    public String code() {
      return "OPEN";
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

  public record Completed() implements State {
    @Override
    public String code() {
      return "UPLOAD_ALREADY_COMPLETED";
    }

    @Override
    public Step complete() {
      return Step.REPLAY; // ⭐ idempotent: same answer, no second side effect
    }
  }

  public record Aborted() implements State {
    @Override
    public String code() {
      return "UPLOAD_ABORTED";
    }

    @Override
    public Step abort() {
      return Step.REPLAY;
    }
  }

  /** A tiny context: counts side effects so the test can prove a replay causes none. */
  public static final class Upload {
    private State state = new Open();
    private int pipelinesStarted;

    public Step complete() {
      Step step = state.complete();
      if (step == Step.PERFORM) {
        state = new Completed();
        pipelinesStarted++;
      }
      return step;
    }

    public Step abort() {
      Step step = state.abort();
      if (step == Step.PERFORM) {
        state = new Aborted();
      }
      return step;
    }

    public int pipelinesStarted() {
      return pipelinesStarted;
    }
  }
}
