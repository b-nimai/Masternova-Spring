package com.masternova.api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Fails the build when one module reaches into another module's internals. */
class ModularityTests {

  private final ApplicationModules modules = ApplicationModules.of(ApiApplication.class);

  @Test
  void modulesRespectTheirBoundaries() {
    modules.verify();
  }

  /** Writes PlantUML component diagrams + a module canvas to target/spring-modulith-docs. */
  @Test
  void writeModuleDocumentation() {
    new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
  }
}
