package com.masternova.worker.pipeline.job;

import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * FACTORY METHOD / REGISTRY: job type → its processor, built from every {@link JobProcessor} bean
 * Spring finds. ⭐ Checked at STARTUP — a type with no processor, or two, fails the boot instead of
 * a job at 3 a.m. (the same shape as the outbox's handler registry, catalog row 9).
 */
@Component
@DesignPattern(
    value = Pattern.FACTORY_METHOD,
    role = "Registry",
    note = "patterns/docs/09-factory-method-registry.md")
public class JobProcessorRegistry {

  private final Map<JobType, JobProcessor<?>> byType = new EnumMap<>(JobType.class);

  public JobProcessorRegistry(List<JobProcessor<?>> processors) {
    for (JobProcessor<?> processor : processors) {
      JobProcessor<?> previous = byType.putIfAbsent(processor.type(), processor);
      if (previous != null) {
        throw new IllegalStateException(
            "two JobProcessors for " + processor.type() + ": " + previous + ", " + processor);
      }
    }
    for (JobType type : JobType.values()) {
      if (!byType.containsKey(type)) {
        throw new IllegalStateException("no JobProcessor for " + type);
      }
    }
  }

  public JobProcessor<?> of(JobType type) {
    return byType.get(type);
  }
}
