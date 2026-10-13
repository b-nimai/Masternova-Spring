package com.masternova.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StorageAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class));

  @Test
  void anyAppWithTheJarGetsObjectStorageWithoutConnectingAnywhere() {
    runner
        .withPropertyValues("masternova.storage.endpoint=http://localhost:1") // nothing listens
        .run(
            context -> {
              assertThat(context).hasSingleBean(ObjectStorage.class);
              assertThat(context.getBean(ObjectStorage.class).bucket())
                  .isEqualTo("masternova-media");
            });
  }

  @Test
  void browserUrlsUseThePublicEndpointAndServerUrlsTheInternalOne() {
    runner
        .withPropertyValues(
            "masternova.storage.endpoint=http://seaweedfs:8333",
            "masternova.storage.public-endpoint=http://localhost:9010",
            "masternova.storage.path-style=true",
            "masternova.storage.access-key=a",
            "masternova.storage.secret-key=b")
        .run(
            context -> {
              ObjectStorage storage = context.getBean(ObjectStorage.class);
              assertThat(storage.presignDownload("k", java.time.Duration.ofMinutes(1)))
                  .hasHost("localhost")
                  .hasPort(9010)
                  .hasPath("/masternova-media/k");
              assertThat(storage.presignInternalDownload("k", java.time.Duration.ofMinutes(1)))
                  .hasHost("seaweedfs")
                  .hasPort(8333);
            });
  }

  @Test
  void anEmptyPublicEndpointMeansTheSameHost() { // what `${STORAGE_PUBLIC_ENDPOINT:}` binds to
    runner
        .withPropertyValues(
            "masternova.storage.endpoint=http://localhost:9010",
            "masternova.storage.public-endpoint=",
            "masternova.storage.access-key=a", // presigning needs credentials; CI has none ambient
            "masternova.storage.secret-key=b")
        .run(
            context ->
                assertThat(
                        context
                            .getBean(ObjectStorage.class)
                            .presignDownload("k", java.time.Duration.ofMinutes(1)))
                    .hasPort(9010));
  }

  @Test
  void aBlankBucketFailsStartup() {
    runner
        .withPropertyValues("masternova.storage.bucket= ")
        .run(context -> assertThat(context).hasFailed());
  }
}
