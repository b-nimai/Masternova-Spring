package com.masternova.api.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.kernel.event.DomainEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ⭐ Spring Modulith's module test: boots ONLY the platform module (its own packages + whatever it
 * depends on). If platform secretly needed a bean from identity or catalog, this would fail to
 * start — the runtime counterpart of ModularityTests' static check.
 */
@ApplicationModuleTest(extraIncludes = {}) // STANDALONE: just this module
@Import(TestcontainersConfiguration.class)
class PlatformModuleIT {

  record ModuleSmokeEvent(String aggregateId) implements DomainEvent {
    @Override
    public String type() {
      return "platform.module-smoke.v1";
    }
  }

  @Autowired ApplicationContext context;
  @Autowired EventPublisher events;
  @Autowired PlatformTransactionManager transactions;
  @Autowired JdbcClient jdbc;

  @Test
  void thePlatformModuleStartsOnItsOwn() {
    assertThat(context.getBeansOfType(EventPublisher.class)).hasSize(1);
    assertThat(context.getBeansOfType(MasternovaProperties.class)).hasSize(1);
  }

  @Test
  void itsPublicApiWorksEndToEnd() {
    new TransactionTemplate(transactions)
        .executeWithoutResult(s -> events.publish(new ModuleSmokeEvent("smoke-1")));

    assertThat(
            jdbc.sql("SELECT count(*) FROM outbox_message WHERE aggregate_id = 'smoke-1'")
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }
}
