package com.masternova.java.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.java.exceptions.ProblemMapper.Problem;
import com.masternova.java.exceptions.ValidationException.FieldError;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProblemMapperTest {

  @Test
  void eachDomainExceptionHasItsStatusAndStableCode() {
    assertThat(ProblemMapper.toProblem(new NotFoundException("Course", "c42")))
        .extracting(Problem::status, Problem::code)
        .containsExactly(404, "NOT_FOUND");
    assertThat(ProblemMapper.toProblem(new ValidationException(List.of(new FieldError("title", "NotBlank")))))
        .extracting(Problem::status, Problem::code)
        .containsExactly(400, "VALIDATION_FAILED");
    assertThat(ProblemMapper.toProblem(new RuleViolationException("COUPON_EXPIRED", "coupon expired")))
        .extracting(Problem::status, Problem::code)
        .containsExactly(422, "COUPON_EXPIRED");
  }

  @Test
  void conflictCarriesBothVersionsForTheClient() {
    Problem problem = ProblemMapper.toProblem(new VersionConflictException(7, 8));

    assertThat(problem.status()).isEqualTo(409);
    assertThat(problem.extras()).containsEntry("expectedVersion", 7L).containsEntry("currentVersion", 8L);
  }

  @Test
  void unexpectedErrorsNeverLeakTheirMessage() {
    Problem problem = ProblemMapper.unexpected(new NullPointerException("password=hunter2"));

    assertThat(problem.status()).isEqualTo(500);
    assertThat(problem.detail()).doesNotContain("hunter2");
  }

  @Test
  void domainExceptionsAreUnchecked() {
    // RuntimeException → no `throws` clause needed, and @Transactional rolls back on it.
    assertThat(new NotFoundException("Course", "c1")).isInstanceOf(RuntimeException.class);
  }

  @Test
  void validationNeedsAtLeastOneError() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ValidationException(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
