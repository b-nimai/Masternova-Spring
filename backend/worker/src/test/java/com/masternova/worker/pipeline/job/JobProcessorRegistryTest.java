package com.masternova.worker.pipeline.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.masternova.storage.ObjectStorage;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobProcessorRegistryTest {

  static JobProcessor<Void> processorFor(JobType type) {
    return new JobProcessor<>(mock(ObjectStorage.class)) {
      @Override
      public JobType type() {
        return type;
      }

      @Override
      protected Work<Void> process(Job job, Path dir, ProgressSink progress) {
        return new Work<>(List.of(), null);
      }
    };
  }

  static List<JobProcessor<?>> oneOfEach() {
    return Arrays.stream(JobType.values())
        .<JobProcessor<?>>map(JobProcessorRegistryTest::processorFor)
        .toList();
  }

  @Test
  void everyTypeResolvesToItsProcessor() {
    JobProcessorRegistry registry = new JobProcessorRegistry(oneOfEach());
    for (JobType type : JobType.values()) {
      assertThat(registry.of(type).type()).isEqualTo(type);
    }
  }

  @Test
  void aMissingTypeFailsAtStartupNotAtThreeAm() {
    List<JobProcessor<?>> noPackage =
        oneOfEach().stream().filter(p -> p.type() != JobType.PACKAGE).toList();
    assertThatThrownBy(() -> new JobProcessorRegistry(noPackage))
        .hasMessageContaining("no JobProcessor for PACKAGE");
  }

  @Test
  void twoProcessorsForOneTypeFailAtStartup() {
    var twice = new java.util.ArrayList<>(oneOfEach());
    twice.add(processorFor(JobType.PROBE));
    assertThatThrownBy(() -> new JobProcessorRegistry(twice))
        .hasMessageContaining("two JobProcessors for PROBE");
  }
}
