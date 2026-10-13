package com.masternova.worker;

import com.masternova.storage.SeaweedFsContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres (with pgvector, same image as compose.yaml), Redis and S3 (SeaweedFS) for
 * integration tests. Storage has no service connection, so a {@link DynamicPropertyRegistrar}
 * points {@code masternova.storage.*} at its container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(
        DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
  }

  @Bean
  @ServiceConnection(name = "redis")
  GenericContainer<?> redisContainer() {
    return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
  }

  @Bean
  SeaweedFsContainer seaweedFsContainer() {
    return new SeaweedFsContainer();
  }

  @Bean
  DynamicPropertyRegistrar storageProperties(SeaweedFsContainer seaweed) {
    return registry -> {
      registry.add("masternova.storage.endpoint", seaweed::endpoint);
      registry.add("masternova.storage.access-key", () -> SeaweedFsContainer.ACCESS_KEY);
      registry.add("masternova.storage.secret-key", () -> SeaweedFsContainer.SECRET_KEY);
    };
  }
}
