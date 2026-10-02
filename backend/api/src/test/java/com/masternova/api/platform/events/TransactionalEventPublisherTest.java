package com.masternova.api.platform.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.platform.EventPublisher;
import com.masternova.kernel.event.DomainEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Observer timing relative to the transaction — no database needed: a transaction manager that only
 * records begin/commit/rollback is enough for Spring's transaction synchronisation.
 */
class TransactionalEventPublisherTest {

  record CourseDrafted(String aggregateId) implements DomainEvent {
    @Override
    public String type() {
      return "catalog.course-drafted.v1";
    }
  }

  /**
   * Records begin/commit/rollback. ⭐ Like a real transaction manager it BINDS the transaction to
   * the current thread — that binding is how a nested @Transactional(MANDATORY / REQUIRED) call
   * finds the transaction that is already running. (Without isExistingTransaction, every call would
   * look like the first one.)
   */
  static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
    final List<String> log;

    RecordingTransactionManager(List<String> log) {
      this.log = log;
    }

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
      return TransactionSynchronizationManager.hasResource(this);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      TransactionSynchronizationManager.bindResource(this, Boolean.TRUE);
      log.add("tx: begin");
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      log.add("tx: commit");
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      log.add("tx: rollback");
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
      TransactionSynchronizationManager.unbindResourceIfPossible(this);
    }
  }

  /** Two observers with different timing. */
  static final class Observers {
    final List<String> log;

    Observers(List<String> log) {
      this.log = log;
    }

    @EventListener // immediately, inside the transaction
    void during(CourseDrafted event) {
      log.add("@EventListener " + event.aggregateId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // only if it commits
    void afterCommit(CourseDrafted event) {
      log.add("@TransactionalEventListener " + event.aggregateId());
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  static class Config {
    final List<String> log = new ArrayList<>();

    @Bean
    List<String> eventLog() {
      return log;
    }

    @Bean
    RecordingTransactionManager transactionManager() {
      return new RecordingTransactionManager(log);
    }

    @Bean
    TransactionalEventPublisher publisher(
        org.springframework.context.ApplicationEventPublisher spring) {
      return new TransactionalEventPublisher(spring);
    }

    @Bean
    Observers observers() {
      return new Observers(log);
    }
  }

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @SuppressWarnings("unchecked")
  private static List<String> log(org.springframework.context.ApplicationContext ctx) {
    return ctx.getBean("eventLog", List.class);
  }

  @Test
  void afterCommitObserversRunOnlyOnceTheTransactionCommits() {
    runner.run(
        ctx -> {
          new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class))
              .executeWithoutResult(
                  s -> ctx.getBean(EventPublisher.class).publish(new CourseDrafted("c1")));

          assertThat(log(ctx))
              .containsExactly(
                  "tx: begin",
                  "@EventListener c1", //                 during the transaction
                  "tx: commit",
                  "@TransactionalEventListener c1"); //   after the commit
        });
  }

  @Test
  void afterCommitObserversNeverSeeARolledBackChange() {
    runner.run(
        ctx -> {
          assertThatThrownBy(
                  () ->
                      new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class))
                          .executeWithoutResult(
                              s -> {
                                ctx.getBean(EventPublisher.class).publish(new CourseDrafted("c2"));
                                throw new IllegalStateException(
                                    "the change failed after publishing");
                              }))
              .isInstanceOf(IllegalStateException.class);

          // ⭐ the in-transaction listener already ran (it can't be undone) — the after-commit one
          // never did
          assertThat(log(ctx)).containsExactly("tx: begin", "@EventListener c2", "tx: rollback");
        });
  }

  @Test
  void publishingOutsideATransactionIsRefused() {
    runner.run(
        ctx ->
            assertThatThrownBy(
                    () -> ctx.getBean(EventPublisher.class).publish(new CourseDrafted("c3")))
                .isInstanceOf(IllegalTransactionStateException.class));
  }
}
