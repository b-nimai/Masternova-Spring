package com.masternova.api.learning.aop;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/** A real @Aspect, and the self-invocation blind spot every proxy has. Study note §3–§4. */
class AspectLearningTest {

  /** Marks methods whose duration should be logged. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface LogExecutionTime {}

  static final class EventLog {
    final List<String> lines = new ArrayList<>();
  }

  /**
   * ⭐ An aspect = WHERE (the pointcut: methods annotated @LogExecutionTime) + WHAT (the advice:
   * code around the call). Spring wraps every matching bean in a proxy that runs this.
   */
  @Aspect
  static final class ExecutionTimeAspect {
    private final EventLog log;

    ExecutionTimeAspect(EventLog log) {
      this.log = log;
    }

    @Around("@annotation(com.masternova.api.learning.aop.AspectLearningTest.LogExecutionTime)")
    Object time(ProceedingJoinPoint call) throws Throwable {
      long start = System.nanoTime();
      try {
        return call.proceed(); // ⭐ run the real method (skip this and the method never runs)
      } finally {
        long micros = (System.nanoTime() - start) / 1_000;
        log.lines.add(
            call.getSignature().getName() + " took " + (micros >= 0 ? "some" : "?") + " µs");
      }
    }
  }

  /** Not final: Spring must be able to subclass it (CGLIB). */
  static class CourseService {
    @LogExecutionTime
    public String publish(String courseId) {
      return "published " + courseId;
    }

    /** Calls publish() on `this` — the RAW object, not the proxy. */
    public List<String> publishAll(List<String> courseIds) {
      return courseIds.stream().map(this::publish).toList();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAspectJAutoProxy // (Spring Boot's AopAutoConfiguration turns this on in the real app)
  static class AopConfig {
    @Bean
    EventLog eventLog() {
      return new EventLog();
    }

    @Bean
    ExecutionTimeAspect executionTimeAspect(EventLog log) {
      return new ExecutionTimeAspect(log);
    }

    @Bean
    CourseService courseService() {
      return new CourseService();
    }
  }

  @Test
  void callsThroughTheProxyAreAdvised() {
    new ApplicationContextRunner()
        .withUserConfiguration(AopConfig.class)
        .run(
            ctx -> {
              CourseService service = ctx.getBean(CourseService.class);

              assertThat(service.publish("c1")).isEqualTo("published c1");
              assertThat(ctx.getBean(EventLog.class).lines).containsExactly("publish took some µs");
              assertThat(service.getClass().getName())
                  .contains("$$SpringCGLIB$$"); // the bean IS a proxy
            });
  }

  @Test
  void selfInvocationBypassesTheAspect() {
    new ApplicationContextRunner()
        .withUserConfiguration(AopConfig.class)
        .run(
            ctx -> {
              ctx.getBean(CourseService.class).publishAll(List.of("c1", "c2"));

              // ⚠️ Two publish() calls happened — but via `this`, inside the target object, so the
              //    proxy never saw them. Same reason @Transactional "doesn't work" on internal
              // calls.
              assertThat(ctx.getBean(EventLog.class).lines).isEmpty();
            });
  }
}
