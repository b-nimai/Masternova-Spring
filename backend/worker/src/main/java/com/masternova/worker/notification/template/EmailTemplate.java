package com.masternova.worker.notification.template;

import com.masternova.kernel.notification.NotificationCategory;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * ⭐ TEMPLATE METHOD. {@link #render} is the fixed skeleton every email goes through — subclasses
 * supply only the varying steps (subject, preview, model). Because the skeleton is {@code final},
 * no email can "forget" the plaintext part, the shared layout, or the unsubscribe footer, and an
 * opt-out-able email without an unsubscribe link fails here, before anything is sent.
 *
 * @param <P> the payload this email needs — typed, so the handler can't pass the wrong data
 */
@DesignPattern(
    value = Pattern.TEMPLATE_METHOD,
    role = "AbstractClass",
    note = "patterns/docs/06-template-method.md")
public abstract class EmailTemplate<P> {

  private final ITemplateEngine engine;

  protected EmailTemplate(ITemplateEngine engine) {
    this.engine = Objects.requireNonNull(engine, "engine");
  }

  /** The file name under templates/email/ (both .html and .txt) and the delivery-claim key. */
  public abstract String key();

  public abstract NotificationCategory category();

  // ---- the steps that vary (primitive operations) ----

  protected abstract String subject(P payload);

  /** The inbox preview line shown after the subject (hidden in the body). */
  protected abstract String preview(P payload);

  /** The variables the content template uses. */
  protected abstract Map<String, Object> model(P payload);

  // ---- the skeleton (template method) ----

  public final RenderedEmail render(P payload, RenderContext ctx) {
    Objects.requireNonNull(payload, "payload");
    if (!category().mandatory() && ctx.unsubscribeUrl().isEmpty()) {
      // an optional email MUST carry a way out — the law in many places, and good manners anywhere
      throw new IllegalStateException(key() + " is " + category() + ": needs an unsubscribe link");
    }
    String subject = subject(payload);

    Context context = new Context(Locale.ENGLISH);
    context.setVariables(model(payload));
    context.setVariable("subject", subject);
    context.setVariable("preview", preview(payload));
    context.setVariable("webUrl", ctx.webUrl().toString());
    context.setVariable("unsubscribeUrl", ctx.unsubscribeUrl().map(Object::toString).orElse(null));

    String html = engine.process("email/" + key() + ".html", context); // through layout.html
    String text = engine.process("email/" + key() + ".txt", context) + footer(ctx);
    return new RenderedEmail(subject, html, text);
  }

  /** The plaintext twin of the HTML footer. A hook with a default: subclasses rarely change it. */
  protected String footer(RenderContext ctx) {
    StringBuilder footer = new StringBuilder("\n\n—\nMasternova · ").append(ctx.webUrl());
    ctx.unsubscribeUrl()
        .ifPresent(url -> footer.append("\nDon't want these emails? Unsubscribe: ").append(url));
    return footer.append('\n').toString();
  }
}
