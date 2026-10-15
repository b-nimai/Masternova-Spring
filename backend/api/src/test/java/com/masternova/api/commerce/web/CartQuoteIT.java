package com.masternova.api.commerce.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.entitlement.TestCourses;
import com.masternova.api.identity.Role;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Pricing over HTTP with real catalog offers, real entitlement rows and coupons an admin created
 * through the API (9.2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CartQuoteIT {

  @Autowired MockMvcTester mvc;
  @Autowired DataSource dataSource;
  UUID instructor;
  UUID learner;
  UUID admin;
  UUID java;
  UUID spring;

  @BeforeEach
  void setUp() {
    TestInstructors people = new TestInstructors(dataSource);
    instructor = people.create("Asha Rao").id();
    learner = people.create("Lena Learner").id();
    admin = people.create("Ada Admin").id();
    TestCourses courses = new TestCourses(dataSource);
    java = courses.create(instructor, "PUBLISHED", 49_900, true, null).courseId();
    spring = courses.create(instructor, "PUBLISHED", 79_900, true, null).courseId();
  }

  @AfterEach
  void clean() {
    new TestInstructors(dataSource).deleteAll();
  }

  static RequestPostProcessor as(UUID id, Role role) {
    return jwt()
        .jwt(j -> j.subject(id.toString()).claim("roles", List.of(role.name())))
        .authorities(List.<GrantedAuthority>of(new SimpleGrantedAuthority(role.authority())));
  }

  private MockMvcTester.MockMvcRequestBuilder quote(String body) {
    return mvc.post()
        .uri("/api/v1/cart/quote")
        .with(as(learner, Role.LEARNER))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private void createCoupon(String json) {
    assertThat(
            mvc.post()
                .uri("/api/v1/admin/coupons")
                .with(as(admin, Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .hasStatus(HttpStatus.CREATED);
  }

  @Test
  void pricesACart() {
    var body =
        assertThat(quote("{\"courseIds\": [\"%s\", \"%s\"]}".formatted(java, spring)))
            .hasStatusOk()
            .bodyJson();
    body.extractingPath("$.lines[0].courseId").isEqualTo(java.toString());
    body.extractingPath("$.lines[1].priceMinor").isEqualTo(79_900);
    body.extractingPath("$.subtotalMinor").isEqualTo(129_800);
    body.extractingPath("$.discountMinor").isEqualTo(0);
    body.extractingPath("$.totalMinor").isEqualTo(129_800);
    body.extractingPath("$.currency").isEqualTo("INR");
  }

  @Test
  void appliesACouponCaseInsensitively() {
    createCoupon("{\"code\": \"SAVE20\", \"percentOff\": 20, \"maxDiscountMinor\": 20000}");

    var body =
        assertThat(
                quote(
                    "{\"courseIds\": [\"%s\", \"%s\"], \"couponCode\": \"save20\"}"
                        .formatted(java, spring)))
            .hasStatusOk()
            .bodyJson();
    body.extractingPath("$.discountMinor").isEqualTo(20_000); // 20% = 25960, capped
    body.extractingPath("$.totalMinor").isEqualTo(109_800);
    body.extractingPath("$.couponCode").isEqualTo("SAVE20");
  }

  @Test
  void aBadCouponIsA422WithItsCode() {
    createCoupon(
        "{\"code\": \"JAVAONLY\", \"amountOffMinor\": 10000, \"courseId\": \"%s\"}"
            .formatted(java));
    createCoupon("{\"code\": \"OLD\", \"percentOff\": 10, \"endsAt\": \"2020-01-01T00:00:00Z\"}");

    assertThat(quote("{\"courseIds\": [\"%s\"], \"couponCode\": \"JAVAONLY\"}".formatted(spring)))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("COUPON_NOT_APPLICABLE");
    assertThat(quote("{\"courseIds\": [\"%s\"], \"couponCode\": \"OLD\"}".formatted(java)))
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("COUPON_EXPIRED");
    assertThat(quote("{\"courseIds\": [\"%s\"], \"couponCode\": \"NOPE\"}".formatted(java)))
        .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("COUPON_NOT_FOUND");
  }

  @Test
  void aCourseAlreadyHeldCantBeBoughtAgain() {
    assertThat(
            mvc.put()
                .uri("/api/v1/admin/entitlements/{u}/{c}", learner, java)
                .with(as(admin, Role.ADMIN)))
        .hasStatus(HttpStatus.NO_CONTENT);

    var problem =
        assertThat(quote("{\"courseIds\": [\"%s\"]}".formatted(java)))
            .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
            .bodyJson();
    problem.extractingPath("$.code").isEqualTo("COURSE_ALREADY_OWNED");
    problem.extractingPath("$.courseId").isEqualTo(java.toString());
  }

  @Test
  void theCartsShapeAndWhoMayAsk() {
    assertThat(quote("{\"courseIds\": []}"))
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("CART_EMPTY");
    assertThat(quote("{\"courseIds\": [\"%s\"]}".formatted(UUID.randomUUID())))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(
            mvc.post()
                .uri("/api/v1/cart/quote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseIds\": []}"))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void couponsAreAnAdminsAndTheirCodesUnique() {
    String json = "{\"code\": \"ONCE\", \"percentOff\": 5}";
    assertThat(
            mvc.post()
                .uri("/api/v1/admin/coupons")
                .with(as(learner, Role.LEARNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .hasStatus(HttpStatus.FORBIDDEN);
    createCoupon(json);
    assertThat(
            mvc.post()
                .uri("/api/v1/admin/coupons")
                .with(as(admin, Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"once\", \"amountOffMinor\": 100}"))
        .hasStatus(HttpStatus.CONFLICT);
    assertThat(
            mvc.post()
                .uri("/api/v1/admin/coupons")
                .with(as(admin, Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"BOTH\", \"percentOff\": 5, \"amountOffMinor\": 100}"))
        .hasStatus(HttpStatus.BAD_REQUEST);

    assertThat(mvc.post().uri("/api/v1/admin/coupons/once/deactivate").with(as(admin, Role.ADMIN)))
        .bodyJson()
        .extractingPath("$.active")
        .isEqualTo(false);
    assertThat(quote("{\"courseIds\": [\"%s\"], \"couponCode\": \"ONCE\"}".formatted(java)))
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("COUPON_INACTIVE");
  }
}
