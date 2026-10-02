package com.masternova.api;

import org.springframework.boot.SpringApplication;

/**
 * Runs the api against throwaway Testcontainers instead of compose: {@code ./mvnw -pl api
 * spring-boot:test-run}.
 */
public class TestApiApplication {

  public static void main(String[] args) {
    SpringApplication.from(ApiApplication::main).with(TestcontainersConfiguration.class).run(args);
  }
}
