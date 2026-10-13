package com.masternova.worker.pipeline.job;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import com.masternova.storage.ObjectStorage;
import com.masternova.worker.pipeline.PipelineStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * TEMPLATE METHOD: the skeleton every pipeline job runs (video-pipeline LLD §4), with the steps
 * that VARY left to subclasses. {@link #run} is final, so a fifth processor can't forget the
 * invariants that live here:
 *
 * <ol>
 *   <li>a private work directory per job;
 *   <li>{@link #process} — the hook: ffprobe / ffmpeg / a playlist, writing local files;
 *   <li>⭐ upload the outputs with COMMIT MARKERS LAST (a rung's {@code index.m3u8}, the asset's
 *       {@code master.m3u8}): a reader never sees a playlist whose segments aren't all there;
 *   <li>hand back a {@link Completion} — the database effects ({@link #record}, {@link #next}) —
 *       for the runner to apply in ONE transaction with marking the job DONE;
 *   <li>always delete the work directory, even when a step throws.
 * </ol>
 *
 * @param <R> what {@link #process} learned (the probe's {@code SourceInfo}, a rendition …)
 */
@DesignPattern(
    value = Pattern.TEMPLATE_METHOD,
    role = "AbstractClass",
    note = "patterns/docs/06-template-method.md")
public abstract class JobProcessor<R> {

  protected final ObjectStorage storage;

  protected JobProcessor(ObjectStorage storage) {
    this.storage = storage;
  }

  /** The DAG node this processor runs; the registry keys on it. */
  public abstract JobType type();

  /** The skeleton. */
  public final Completion run(Job job, Path workRoot, ProgressSink progress) {
    // ⭐ absolute: ffmpeg runs WITH this as its working directory, so a relative path in its
    //    arguments would resolve twice (found by PipelineIT with a relative work-dir)
    Path dir = workRoot.toAbsolutePath().resolve(job.id().toString());
    try {
      Files.createDirectories(dir);
      Work<R> work = process(job, dir, progress);
      upload(work.outputs());
      R result = work.result();
      return store -> {
        record(job, result, store);
        return next(job, result, store);
      };
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      deleteRecursively(dir);
    }
  }

  /** The step that varies: do the work in {@code dir}, say what to upload and what was learned. */
  protected abstract Work<R> process(Job job, Path dir, ProgressSink progress) throws IOException;

  /** Database effects of a finished job (asset columns, a rendition row). Default: none. */
  protected void record(Job job, R result, PipelineStore store) {}

  /** The jobs this one unlocks (fan-out, or fan-in once siblings are done). Default: none. */
  protected List<JobSpec> next(Job job, R result, PipelineStore store) {
    return List.of();
  }

  private void upload(List<Output> outputs) {
    outputs.stream()
        .sorted(Comparator.comparing(Output::commitMarker)) // false < true: markers go last
        .forEach(o -> storage.put(o.key(), o.file(), o.contentType()));
  }

  private static void deleteRecursively(Path dir) {
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(dir)) {
      paths.sorted(Comparator.reverseOrder()).forEach(JobProcessor::deleteQuietly);
    } catch (IOException e) {
      // a leftover is wiped by the startup cleanup; never fail a job over it
    }
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // see deleteRecursively
    }
  }

  // ------------------------------------------------------------------ the hook's vocabulary

  /** One local file to upload to {@code key}. */
  public record Output(Path file, String key, String contentType, boolean commitMarker) {

    public static Output of(Path file, String key, String contentType) {
      return new Output(file, key, contentType, false);
    }

    public static Output marker(Path file, String key, String contentType) {
      return new Output(file, key, contentType, true);
    }
  }

  /** What {@link #process} produced. */
  public record Work<R>(List<Output> outputs, R result) {}

  /** The database half of a finished job, applied by the runner inside its transaction. */
  @FunctionalInterface
  public interface Completion {
    List<JobSpec> apply(PipelineStore store);
  }
}
