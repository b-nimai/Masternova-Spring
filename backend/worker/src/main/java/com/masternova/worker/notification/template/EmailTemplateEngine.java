package com.masternova.worker.notification.template;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * One Thymeleaf engine for emails, with TWO resolvers: {@code *.html} in HTML mode (escaped by
 * default — a display name like {@code <script>} renders as text) and {@code *.txt} in TEXT mode
 * for the plaintext part. Defining this bean makes Boot's own Thymeleaf engine back off.
 */
@Configuration(proxyBeanMethods = false)
public class EmailTemplateEngine {

  @Bean
  SpringTemplateEngine emailTemplateEngine() {
    return create();
  }

  /** Also used by the template unit tests: no Spring context needed to render an email. */
  public static SpringTemplateEngine create() {
    SpringTemplateEngine engine = new SpringTemplateEngine();
    engine.addTemplateResolver(resolver(TemplateMode.HTML, "*.html", 1));
    engine.addTemplateResolver(resolver(TemplateMode.TEXT, "*.txt", 2));
    return engine;
  }

  private static ClassLoaderTemplateResolver resolver(
      TemplateMode mode, String pattern, int order) {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setTemplateMode(mode);
    resolver.setResolvablePatterns(Set.of(pattern));
    resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
    resolver.setCheckExistence(true);
    resolver.setCacheable(true);
    resolver.setOrder(order);
    return resolver;
  }
}
