package com.masternova.worker.notification.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.thymeleaf.ITemplateEngine;

/** Rendering without Spring: the same engine the worker uses, built by hand. */
class EmailTemplateTest {

  private final ITemplateEngine engine = EmailTemplateEngine.create();
  private final VerifyEmailTemplate verify = new VerifyEmailTemplate(engine);
  private final WelcomeEmailTemplate welcome = new WelcomeEmailTemplate(engine);

  private static final URI WEB = URI.create("http://localhost:4200");
  private static final URI VERIFY_URL = URI.create("http://localhost:4200/verify-email?token=abc");
  private static final URI UNSUBSCRIBE = URI.create("http://localhost:4200/unsubscribe?token=xyz");

  @Test
  void theVerificationEmailHasSubjectHtmlAndAPlaintextTwin() {
    RenderedEmail email =
        verify.render(
            new VerifyEmailTemplate.Payload("Asha", VERIFY_URL),
            new RenderContext(WEB, Optional.empty()));

    assertThat(email.subject()).isEqualTo("Confirm your email for Masternova");
    assertThat(email.html())
        .contains("Hi <strong>Asha</strong>")
        .contains("href=\"" + VERIFY_URL + "\"") // the button …
        .contains("One click and your account is ready.") // … the hidden inbox preview …
        .contains("<title>Confirm your email for Masternova</title>") // … via the shared layout
        .doesNotContain("Unsubscribe"); // mandatory: nothing to unsubscribe from
    assertThat(email.text())
        .contains("Hi Asha,")
        .contains(VERIFY_URL.toString())
        .contains("Masternova · " + WEB) // the footer, added by the skeleton
        .doesNotContain("Unsubscribe");
  }

  @Test
  void userTextIsHtmlEscapedSoANameCannotInjectMarkup() {
    RenderedEmail email =
        verify.render(
            new VerifyEmailTemplate.Payload("<script>alert(1)</script>", VERIFY_URL),
            new RenderContext(WEB, Optional.empty()));

    assertThat(email.html()).contains("&lt;script&gt;").doesNotContain("<script>alert");
  }

  @Test
  void anOptionalEmailCannotBeRenderedWithoutAnUnsubscribeLink() {
    WelcomeEmailTemplate.Payload payload =
        new WelcomeEmailTemplate.Payload("Asha", URI.create("http://localhost:4200/"));

    assertThatThrownBy(() -> welcome.render(payload, new RenderContext(WEB, Optional.empty())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("welcome is PRODUCT_NEWS: needs an unsubscribe link");
  }

  @Test
  void anOptionalEmailCarriesTheUnsubscribeLinkInBothParts() {
    RenderedEmail email =
        welcome.render(
            new WelcomeEmailTemplate.Payload("Asha", URI.create("http://localhost:4200/")),
            new RenderContext(WEB, Optional.of(UNSUBSCRIBE)));

    assertThat(email.subject()).isEqualTo("Welcome to Masternova, Asha");
    assertThat(email.html()).contains("href=\"" + UNSUBSCRIBE + "\"");
    assertThat(email.text()).contains("Unsubscribe: " + UNSUBSCRIBE);
  }

  @Test
  void theSkeletonIsFinalSoNoEmailCanSkipASteps() throws NoSuchMethodException {
    var render = EmailTemplate.class.getMethod("render", Object.class, RenderContext.class);
    assertThat(Modifier.isFinal(render.getModifiers())).isTrue();
  }
}
