package com.masternova.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Masternova HTTP API — a modular monolith.
 *
 * <p>Every direct sub-package of {@code com.masternova.api} is a Spring Modulith application module
 * (a bounded context). Modules talk to each other only through their top-level public types and
 * domain events; everything in a sub-package is internal. {@code ModularityTests} fails the build
 * if that rule is broken.
 */
@SpringBootApplication
public class ApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(ApiApplication.class, args);
  }
}
