package com.masternova.storage;

import com.github.dockerjava.api.command.InspectContainerResponse;
import java.net.URI;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * SeaweedFS's S3 API in a container (the image compose.yaml runs, ADR-0012), shared with the api
 * and worker ITs through this module's test-jar. {@link #storageProperties()} gives the values to
 * register under {@code masternova.storage.*}.
 */
public class SeaweedFsContainer extends GenericContainer<SeaweedFsContainer> {

  public static final String ACCESS_KEY = "test";
  public static final String SECRET_KEY = "test-secret";
  public static final String BUCKET = "masternova-media";
  private static final int S3_PORT = 8333;

  public SeaweedFsContainer() {
    super(DockerImageName.parse("chrislusf/seaweedfs:4.48"));
    withCommand("server", "-s3", "-dir=/data", "-volume.max=0");
    withEnv("AWS_ACCESS_KEY_ID", ACCESS_KEY);
    withEnv("AWS_SECRET_ACCESS_KEY", SECRET_KEY);
    withExposedPorts(S3_PORT);
    waitingFor(Wait.forHttp("/healthz").forPort(S3_PORT));
  }

  /** ⭐ The bucket exists as soon as the container does, as {@code media-init} does in compose. */
  @Override
  protected void containerIsStarted(InspectContainerResponse info) {
    var props = storageProperties();
    try (var s3 = new StorageAutoConfiguration().s3Client(props)) {
      s3.createBucket(b -> b.bucket(props.bucket()));
    }
  }

  public URI endpoint() {
    return URI.create("http://" + getHost() + ":" + getMappedPort(S3_PORT));
  }

  /** Points an app's {@code masternova.storage.*} at this container (api and worker ITs). */
  public void register(org.springframework.test.context.DynamicPropertyRegistry registry) {
    registry.add("masternova.storage.endpoint", this::endpoint);
    registry.add("masternova.storage.access-key", () -> ACCESS_KEY);
    registry.add("masternova.storage.secret-key", () -> SECRET_KEY);
  }

  /** Path-style, static credentials, the media bucket (already created). */
  public StorageProperties storageProperties() {
    return new StorageProperties(
        endpoint(), null, "us-east-1", BUCKET, true, ACCESS_KEY, SECRET_KEY);
  }
}
