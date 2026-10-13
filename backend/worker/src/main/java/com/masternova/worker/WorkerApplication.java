package com.masternova.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Masternova worker — a separate deployable from the api because its resource profile differs:
 * CPU-heavy transcoding and slow I/O (email, storage) must never starve request threads.
 *
 * <p>Grows into: outbox relay (Phase 2), email delivery (Phase 4), transcode pipeline (Phase 7).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling // the pipeline's job poller; the outbox relay's schedule needs it too
public class WorkerApplication {

  public static void main(String[] args) {
    SpringApplication.run(WorkerApplication.class, args);
  }
}
