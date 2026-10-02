package com.masternova.api.learning.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @Transactional is a PROXY feature — proven without a database, using a transaction manager that
 * just records begin/commit/rollback. Study note §5.
 */
class TransactionalProxyLearningTest {

  /** A fake transaction manager: logs what the @Transactional proxy asks it to do. */
  static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
    final List<String> events = new ArrayList<>();

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      events.add("begin");
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      events.add("commit");
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      events.add("rollback");
    }
  }

  static class EnrollmentService {
    final List<Boolean> transactionActive = new ArrayList<>();

    /**
     * ⭐ Read state through a METHOD: method calls on the proxy are forwarded to the real object.
     * Reading the field directly on the proxy reads the PROXY's own (never-initialised) copy.
     */
    public List<Boolean> transactionActive() {
      return transactionActive;
    }

    @Transactional
    public void enroll(String courseId) {
      // was a transaction really running while this method executed?
      transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
    }

    /** ⚠️ Self-invocation: calls enroll() on `this`, bypassing the transactional proxy. */
    public void enrollBatch(List<String> courseIds) {
      courseIds.forEach(this::enroll);
    }

    @Transactional
    public void failUnchecked() {
      throw new IllegalStateException("payment declined");
    }

    @Transactional
    public void failChecked() throws Exception {
      throw new Exception("checked failure");
    }

    @Transactional(rollbackFor = Exception.class)
    public void failCheckedWithRollbackFor() throws Exception {
      throw new Exception("checked failure");
    }
  }

  /** ✅ Fix 1: the batch lives in ANOTHER bean, so every enroll() call goes through the proxy. */
  record BatchEnroller(EnrollmentService enrollments) {
    void enrollAll(List<String> courseIds) {
      courseIds.forEach(enrollments::enroll);
    }
  }

  /** ✅ Fix 2: programmatic transactions — no proxy involved at all. */
  record TemplateEnroller(TransactionTemplate tx, List<Boolean> transactionActive) {
    void enrollAll(List<String> courseIds) {
      tx.executeWithoutResult(
          status ->
              courseIds.forEach(
                  id ->
                      transactionActive.add(
                          TransactionSynchronizationManager.isActualTransactionActive())));
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement // (auto-configured in the real app)
  static class TxConfig {
    @Bean
    RecordingTransactionManager transactionManager() {
      return new RecordingTransactionManager();
    }

    @Bean
    EnrollmentService enrollmentService() {
      return new EnrollmentService();
    }

    @Bean
    BatchEnroller batchEnroller(EnrollmentService enrollments) {
      return new BatchEnroller(enrollments);
    }

    @Bean
    TemplateEnroller templateEnroller(PlatformTransactionManager tm) {
      return new TemplateEnroller(new TransactionTemplate(tm), new ArrayList<>());
    }
  }

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(TxConfig.class);

  @Test
  void aCallThroughTheProxyRunsInATransaction() {
    runner.run(
        ctx -> {
          ctx.getBean(EnrollmentService.class).enroll("c1");

          assertThat(ctx.getBean(EnrollmentService.class).transactionActive())
              .containsExactly(true);
          assertThat(ctx.getBean(RecordingTransactionManager.class).events)
              .containsExactly("begin", "commit");
        });
  }

  @Test
  void selfInvocationSilentlyRunsWithoutATransaction() {
    runner.run(
        ctx -> {
          ctx.getBean(EnrollmentService.class).enrollBatch(List.of("c1", "c2"));

          // ⚠️ @Transactional on enroll() was IGNORED — no exception, no warning, no transaction.
          assertThat(ctx.getBean(EnrollmentService.class).transactionActive())
              .containsExactly(false, false);
          assertThat(ctx.getBean(RecordingTransactionManager.class).events).isEmpty();
        });
  }

  @Test
  void callingFromAnotherBeanGoesThroughTheProxy() {
    runner.run(
        ctx -> {
          ctx.getBean(BatchEnroller.class).enrollAll(List.of("c1", "c2"));

          assertThat(ctx.getBean(EnrollmentService.class).transactionActive())
              .containsExactly(true, true);
          assertThat(ctx.getBean(RecordingTransactionManager.class).events)
              .containsExactly("begin", "commit", "begin", "commit"); // one transaction PER call
        });
  }

  @Test
  void transactionTemplateNeedsNoProxy() {
    runner.run(
        ctx -> {
          TemplateEnroller enroller = ctx.getBean(TemplateEnroller.class);
          enroller.enrollAll(List.of("c1", "c2"));

          assertThat(enroller.transactionActive()).containsExactly(true, true);
          assertThat(ctx.getBean(RecordingTransactionManager.class).events)
              .containsExactly("begin", "commit"); // ONE transaction around the whole batch
        });
  }

  @Test
  void fieldsAreNotProxiedOnlyMethodsAre() {
    runner.run(
        ctx -> {
          EnrollmentService proxy = ctx.getBean(EnrollmentService.class);
          proxy.enroll("c1");

          // The CGLIB proxy is a subclass instance created WITHOUT running its constructor, so its
          // own copy of the field is null. The method call reaches the real object; the field read
          // doesn't.
          assertThat(proxy.transactionActive).isNull();
          assertThat(proxy.transactionActive()).containsExactly(true);
        });
  }

  @Test
  void uncheckedExceptionsRollBack() {
    runner.run(
        ctx -> {
          assertThatThrownBy(() -> ctx.getBean(EnrollmentService.class).failUnchecked())
              .isInstanceOf(IllegalStateException.class);

          assertThat(ctx.getBean(RecordingTransactionManager.class).events)
              .containsExactly("begin", "rollback");
        });
  }

  @Test
  void checkedExceptionsCommitByDefault() {
    runner.run(
        ctx -> {
          assertThatThrownBy(() -> ctx.getBean(EnrollmentService.class).failChecked())
              .isInstanceOf(Exception.class);

          // ⚠️⚠️ The method FAILED — and the transaction COMMITTED. (Note 05 §2.)
          assertThat(ctx.getBean(RecordingTransactionManager.class).events)
              .containsExactly("begin", "commit");
        });
  }

  @Test
  void rollbackForMakesCheckedExceptionsRollBack() {
    runner.run(
        ctx -> {
          assertThatThrownBy(
                  () -> ctx.getBean(EnrollmentService.class).failCheckedWithRollbackFor())
              .isInstanceOf(Exception.class);

          assertThat(ctx.getBean(RecordingTransactionManager.class).events)
              .containsExactly("begin", "rollback");
        });
  }
}
