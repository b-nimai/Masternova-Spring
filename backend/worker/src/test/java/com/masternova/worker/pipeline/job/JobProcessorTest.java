package com.masternova.worker.pipeline.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.masternova.storage.ObjectStorage;
import com.masternova.worker.pipeline.PipelineStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ⭐ The Template Method's invariants, with a fake processor: no Spring, no DB, no ffmpeg. */
class JobProcessorTest {

  @TempDir Path workRoot;
  final ObjectStorage storage = mock(ObjectStorage.class);
  final List<String> uploaded = new ArrayList<>();
  final Job job = new Job(UUID.randomUUID(), UUID.randomUUID(), JobType.TRANSCODE, "360p", 1);

  JobProcessorTest() {
    doAnswer(inv -> uploaded.add(inv.getArgument(0)))
        .when(storage)
        .put(anyString(), any(Path.class), anyString());
  }

  /** Writes three files, declaring the playlist FIRST — the template must still upload it last. */
  class FakeRendition extends JobProcessor<String> {
    boolean fail;
    Path seenDir;

    FakeRendition() {
      super(JobProcessorTest.this.storage);
    }

    @Override
    public JobType type() {
      return JobType.TRANSCODE;
    }

    @Override
    protected Work<String> process(Job job, Path dir, ProgressSink progress) throws IOException {
      seenDir = dir;
      Path playlist = Files.writeString(dir.resolve("index.m3u8"), "#EXTM3U");
      Path seg0 = Files.writeString(dir.resolve("seg_00000.m4s"), "a");
      Path seg1 = Files.writeString(dir.resolve("seg_00001.m4s"), "b");
      if (fail) {
        throw new IllegalStateException("ffmpeg died");
      }
      return new Work<>(
          List.of(
              Output.marker(playlist, "k/index.m3u8", "x"),
              Output.of(seg0, "k/seg_00000.m4s", "x"),
              Output.of(seg1, "k/seg_00001.m4s", "x")),
          "rendition");
    }

    @Override
    protected List<JobSpec> next(Job job, String result, PipelineStore store) {
      return List.of(JobSpec.of(job.assetId(), JobType.PACKAGE));
    }
  }

  @Test
  void commitMarkersAreUploadedLastWhateverOrderTheHookDeclares() {
    new FakeRendition().run(job, workRoot, ProgressSink.NONE);

    assertThat(uploaded).last().isEqualTo("k/index.m3u8");
    assertThat(uploaded)
        .containsExactlyInAnyOrder("k/index.m3u8", "k/seg_00000.m4s", "k/seg_00001.m4s");
  }

  @Test
  void theWorkDirectoryIsDeletedAfterSuccess() {
    FakeRendition processor = new FakeRendition();
    processor.run(job, workRoot, ProgressSink.NONE);

    assertThat(processor.seenDir).doesNotExist();
  }

  @Test
  void theWorkDirectoryIsDeletedEvenWhenTheHookThrowsAndNothingIsUploaded() {
    FakeRendition processor = new FakeRendition();
    processor.fail = true;

    assertThatThrownBy(() -> processor.run(job, workRoot, ProgressSink.NONE))
        .hasMessage("ffmpeg died");
    assertThat(processor.seenDir).doesNotExist();
    assertThat(uploaded).isEmpty();
  }

  @Test
  void databaseEffectsAreDeferredToTheCompletion() {
    var completion = new FakeRendition().run(job, workRoot, ProgressSink.NONE);

    assertThat(completion.apply(mock(PipelineStore.class)))
        .containsExactly(JobSpec.of(job.assetId(), JobType.PACKAGE));
  }
}
