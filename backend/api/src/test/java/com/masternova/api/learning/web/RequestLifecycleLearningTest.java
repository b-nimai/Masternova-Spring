package com.masternova.api.learning.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The journey of one HTTP request through Spring MVC, recorded step by step: Filter →
 * DispatcherServlet → HandlerInterceptor → (validation) → Controller → (advice) → back out.
 *
 * <p>standaloneSetup = plain Spring MVC around ONE controller: no Boot context, no security, no DB.
 * Study note: {@code patterns/java/10-request-lifecycle-and-jpa.md} §1–§3.
 */
class RequestLifecycleLearningTest {

  private final List<String> log = new ArrayList<>();
  private MockMvcTester mvc;

  // ---- the request/response DTOs (records in web/dto/ in real modules — CLAUDE.md §3)
  record CreateCourseRequest(@NotBlank String title, @PositiveOrZero long priceMinor) {}

  record CourseResponse(long id, String title, long priceMinor) {}

  static final class CourseNotFoundException extends RuntimeException {
    CourseNotFoundException(long id) {
      super("Course " + id + " was not found");
    }
  }

  // ---- 1. a servlet Filter: OUTSIDE Spring MVC, sees every request first and last
  final class RecordingFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
        HttpServletRequest req, HttpServletResponse res, FilterChain chain)
        throws ServletException, IOException {
      log.add("filter: before");
      chain.doFilter(req, res); // ⭐ pass the request on — forget this and nothing else runs
      log.add("filter: after");
    }
  }

  // ---- 2. a HandlerInterceptor: INSIDE Spring MVC, knows which controller method will handle it
  final class RecordingInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
      log.add("interceptor: preHandle");
      return true; // false = stop here (e.g. a rate limiter)
    }

    @Override
    public void postHandle(
        HttpServletRequest req, HttpServletResponse res, Object handler, ModelAndView mv) {
      log.add("interceptor: postHandle"); // ⚠️ skipped when the controller throws
    }

    @Override
    public void afterCompletion(
        HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
      log.add("interceptor: afterCompletion"); // always runs — cleanup goes here
    }
  }

  // ---- 3. the controller
  @RestController
  @RequestMapping("/api/v1/courses")
  final class CourseController {
    @PostMapping
    ResponseEntity<CourseResponse> create(@Valid @RequestBody CreateCourseRequest request) {
      log.add("controller: create");
      CourseResponse created = new CourseResponse(42, request.title(), request.priceMinor());
      return ResponseEntity.created(URI.create("/api/v1/courses/42"))
          .body(created); // 201 + Location
    }

    @GetMapping("/{id}")
    CourseResponse get(@PathVariable long id) {
      log.add("controller: get");
      throw new CourseNotFoundException(id);
    }
  }

  // ---- 4. the exception → Problem Details translator (our GlobalExceptionHandler's job)
  @RestControllerAdvice
  final class RecordingAdvice extends ResponseEntityExceptionHandler {
    @ExceptionHandler(CourseNotFoundException.class)
    ProblemDetail notFound(CourseNotFoundException e) {
      log.add("advice: notFound");
      return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException e,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {
      log.add("advice: validation");
      ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Invalid request");
      problem.setProperty(
          "errors",
          e.getFieldErrors().stream().map(f -> f.getField() + ":" + f.getCode()).sorted().toList());
      return ResponseEntity.status(status).body(problem);
    }
  }

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.create(
            MockMvcBuilders.standaloneSetup(new CourseController())
                .addFilters(new RecordingFilter())
                .addInterceptors(new RecordingInterceptor())
                .setControllerAdvice(new RecordingAdvice())
                .build());
  }

  @Test
  void theHappyPathGoesInAndBackOutInMirrorOrder() {
    assertThat(
            mvc.post()
                .uri("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Java Streams\",\"priceMinor\":99900}"))
        .hasStatus(HttpStatus.CREATED)
        .hasHeader("Location", "/api/v1/courses/42")
        .bodyJson()
        .extractingPath("$.title")
        .isEqualTo("Java Streams");

    assertThat(log)
        .containsExactly(
            "filter: before",
            "interceptor: preHandle",
            "controller: create",
            "interceptor: postHandle",
            "interceptor: afterCompletion",
            "filter: after");
  }

  @Test
  void anExceptionSkipsPostHandleAndGoesThroughTheAdvice() {
    assertThat(mvc.get().uri("/api/v1/courses/7"))
        .hasStatus(HttpStatus.NOT_FOUND)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("Course 7 was not found");

    assertThat(log)
        .containsExactly(
            "filter: before",
            "interceptor: preHandle",
            "controller: get",
            "advice: notFound", //               the exception is turned into a response …
            "interceptor: afterCompletion", //   … postHandle never ran
            "filter: after");
  }

  @Test
  void invalidInputNeverReachesTheController() {
    assertThat(
            mvc.post()
                .uri("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"  \",\"priceMinor\":-1}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors")
        .asArray()
        .containsExactly("priceMinor:PositiveOrZero", "title:NotBlank");

    // ⭐ @Valid runs while binding the arguments — BEFORE the method body. No "controller" entry.
    assertThat(log)
        .containsExactly(
            "filter: before",
            "interceptor: preHandle",
            "advice: validation",
            "interceptor: afterCompletion",
            "filter: after");
  }
}
