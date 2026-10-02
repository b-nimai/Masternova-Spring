package com.masternova.patterns.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistryTest {

  static final class Recording implements Handler {
    final String type;
    final List<String> seen = new ArrayList<>();

    Recording(String type) {
      this.type = type;
    }

    @Override
    public String type() {
      return type;
    }

    @Override
    public void handle(Message message) {
      seen.add(message.payload());
    }
  }

  @Test
  void dispatchesByTypeWithoutASwitch() {
    Recording signup = new Recording("user-registered");
    Recording verified = new Recording("email-verified");
    HandlerRegistry registry = new HandlerRegistry(List.of(signup, verified));

    assertThat(registry.dispatch(new Message("email-verified", "u1")))
        .isEqualTo(new HandlerRegistry.Dispatch.Delivered("email-verified"));
    assertThat(verified.seen).containsExactly("u1");
    assertThat(signup.seen).isEmpty();
  }

  @Test
  void anUnknownTypeIsAnOutcomeNotAnException() {
    HandlerRegistry registry = new HandlerRegistry(List.of(new Recording("a")));

    String text =
        switch (registry.dispatch(new Message("b", "x"))) { // ⭐ exhaustive over the sealed type
          case HandlerRegistry.Dispatch.Delivered d -> "delivered " + d.type();
          case HandlerRegistry.Dispatch.NoHandler n -> "nobody handles " + n.type();
        };
    assertThat(text).isEqualTo("nobody handles b");
  }

  @Test
  void twoHandlersForOneTypeFailAtConstruction() {
    assertThatThrownBy(() -> new HandlerRegistry(List.of(new Recording("a"), new Recording("a"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("two handlers registered for a");
  }

  @Test
  void theRegistryIsImmutableAfterConstruction() {
    HandlerRegistry registry = new HandlerRegistry(List.of(new Recording("a")));
    assertThatThrownBy(() -> registry.types().add("b"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void aFactoryRegistryCreatesAFreshProductEveryTime() {
    ExporterFactory.Exporter first = ExporterFactory.create("csv");
    ExporterFactory.Exporter second = ExporterFactory.create("csv");
    first.add("a");

    assertThat(first).isNotSameAs(second); // ⭐ stateful products are never shared
    assertThat(first.result()).isEqualTo("a\n");
    assertThat(second.result()).isEmpty();
    assertThat(ExporterFactory.create("jsonl")).isNotInstanceOf(first.getClass());
  }

  @Test
  void anUnknownFormatListsTheKnownOnes() {
    assertThatThrownBy(() -> ExporterFactory.create("xml"))
        .hasMessageContaining("known: [csv, jsonl]");
  }
}
