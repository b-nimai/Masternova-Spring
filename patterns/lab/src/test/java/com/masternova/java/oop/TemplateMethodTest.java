package com.masternova.java.oop;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.java.oop.template.EmailTemplate;
import com.masternova.java.oop.template.ReceiptEmail;
import com.masternova.java.oop.template.WelcomeEmail;
import com.masternova.java.valueobject.Money;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class TemplateMethodTest {

  @Test
  void everyEmailFollowsTheSameSkeleton() {
    String welcome = new WelcomeEmail().render("Asha");
    String receipt = new ReceiptEmail("Java Streams", Money.of(999_00, "INR")).render("Ravi");

    assertThat(welcome).startsWith("Hi Asha,").contains("Happy learning").endsWith("/u/onboarding");
    assertThat(receipt)
        .startsWith("Hi Ravi,")
        .contains("You paid INR 999.00 for \"Java Streams\".")
        .contains("Reply to this email") // the overridden hook
        .endsWith("/u/receipts");
  }

  @Test
  void theSkeletonCannotBeOverridden() throws NoSuchMethodException {
    assertThat(Modifier.isFinal(EmailTemplate.class.getMethod("render", String.class).getModifiers()))
        .isTrue();
  }
}
