package com.masternova.api.enrollment.infrastructure;

import com.masternova.api.catalog.CatalogApi;
import com.masternova.api.enrollment.application.ProgressWriter;
import com.masternova.api.enrollment.domain.EnrollmentRepository;
import com.masternova.api.enrollment.domain.LectureProgressRepository;
import com.masternova.api.platform.PublicEndpoints;
import com.masternova.api.platform.PublicEndpoints.Endpoint;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class EnrollmentConfig {

  /** A closing page's beacon can't send the bearer: its token is in the body (ADR-0022). */
  @Bean
  PublicEndpoints enrollmentPublicEndpoints() {
    return () -> List.of(new Endpoint(HttpMethod.POST, "/api/v1/progress/beacon"));
  }

  /**
   * ⭐ The decoration, visible in one place: the write-back buffer wraps the validated
   * write-through. Only the wrapper is a bean, so nothing can inject the unbuffered store by
   * accident.
   */
  @Bean
  WriteBackProgressStore progressStore(
      CatalogApi catalog,
      EnrollmentRepository enrollments,
      LectureProgressRepository progress,
      TransactionTemplate tx,
      StringRedisTemplate redis,
      MeterRegistry meters) {
    return new WriteBackProgressStore(
        new ProgressWriter(catalog, enrollments, progress, tx, meters), redis, meters);
  }
}
