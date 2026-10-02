package com.masternova.api.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.platform.ConflictException;
import com.masternova.api.platform.ForbiddenException;
import com.masternova.api.platform.NotFoundException;
import com.masternova.api.platform.RuleViolationException;
import com.masternova.api.platform.ValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Every error kind → its status, stable code, type URI and details. */
class GlobalExceptionHandlerTest {

  record CreateRequest(@NotBlank String title, @PositiveOrZero long priceMinor) {}

  @RestController
  static class FailingController {
    @GetMapping("/not-found")
    void notFound() {
      throw new NotFoundException("Course", "c42");
    }

    @GetMapping("/conflict")
    void conflict() {
      throw ConflictException.versionConflict(7, 8);
    }

    @GetMapping("/rule")
    void rule() {
      throw new RuleViolationException("COUPON_EXPIRED", "This coupon has expired.");
    }

    @GetMapping("/forbidden")
    void forbidden() {
      throw new ForbiddenException("NO_ENTITLEMENT", "You do not have access to this lecture.");
    }

    @GetMapping("/invalid")
    void invalid() {
      throw ValidationException.of("slug", "Taken", "This slug is already used.");
    }

    @GetMapping("/bug")
    void bug() {
      throw new NullPointerException("password=hunter2 was null"); // must never reach the client
    }

    @PostMapping("/courses")
    void create(@Valid @RequestBody CreateRequest request) {}
  }

  private final MockMvcTester mvc =
      MockMvcTester.create(
          MockMvcBuilders.standaloneSetup(new FailingController())
              .setControllerAdvice(new GlobalExceptionHandler())
              .build());

  @Test
  void notFound() {
    assertThat(mvc.get().uri("/not-found"))
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"type":"https://masternova.dev/problems/not-found","status":404,"code":"NOT_FOUND",
             "detail":"Course c42 was not found","instance":"/not-found","resource":"Course"}
            """);
  }

  @Test
  void versionConflictCarriesBothVersions() {
    assertThat(mvc.get().uri("/conflict"))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code":"VERSION_CONFLICT","expectedVersion":7,"currentVersion":8}
            """);
  }

  @Test
  void ruleViolationIs422WithTheRulesOwnCode() {
    assertThat(mvc.get().uri("/rule"))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code":"COUPON_EXPIRED","type":"https://masternova.dev/problems/coupon-expired"}
            """);
  }

  @Test
  void forbiddenCarriesAReason() {
    assertThat(mvc.get().uri("/forbidden"))
        .hasStatus(HttpStatus.FORBIDDEN)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code":"FORBIDDEN","reason":"NO_ENTITLEMENT"}
            """);
  }

  @Test
  void domainAndBeanValidationShareOneErrorsShape() {
    assertThat(mvc.get().uri("/invalid"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code":"VALIDATION_FAILED","errors":[{"field":"slug","code":"Taken"}]}
            """);

    assertThat(
            mvc.post()
                .uri("/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"priceMinor\":-1}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code":"VALIDATION_FAILED",
             "errors":[{"field":"priceMinor","code":"PositiveOrZero"},{"field":"title","code":"NotBlank"}]}
            """);
  }

  @Test
  void springsOwnErrorsGetACodeToo() {
    assertThat(mvc.delete().uri("/not-found"))
        .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code":"METHOD_NOT_ALLOWED","status":405}
            """);
    assertThat(
            mvc.post().uri("/courses").contentType(MediaType.APPLICATION_JSON).content("{not json"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code":"BAD_REQUEST"}
            """);
  }

  @Test
  void bugsBecomeA500ThatLeaksNothing() {
    assertThat(mvc.get().uri("/bug"))
        .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code":"INTERNAL","detail":"Something went wrong."}
            """);
    assertThat(mvc.get().uri("/bug")).body().asString().doesNotContain("hunter2");
  }
}
