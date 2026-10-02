package com.masternova.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/** Boots the worker against real Postgres + Redis. */
@SpringBootTest(properties = "management.health.mail.enabled=false")
@Import(TestcontainersConfiguration.class)
class WorkerApplicationIT {

  @Autowired ApplicationContext context;

  @Test
  void contextLoads() {
    assertThat(context.getBean(WorkerApplication.class)).isNotNull();
  }
}
