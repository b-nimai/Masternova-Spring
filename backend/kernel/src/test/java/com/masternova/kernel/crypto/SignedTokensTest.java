package com.masternova.kernel.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SignedTokensTest {

  final SignedTokens tokens = new SignedTokens("a-secret-that-is-long-enough-0123456789", "test");

  @Test
  void aSignedPayloadComesBackUnchanged() {
    assertThat(tokens.verify(tokens.sign("a.b.123"))).contains("a.b.123");
  }

  @Test
  void anotherKeyOrAMangledTokenGivesNothing() {
    String t = tokens.sign("payload");
    SignedTokens other = new SignedTokens("another-secret-that-is-long-enough-01234", "test");

    assertThat(other.verify(t)).isEmpty();
    assertThat(tokens.verify(t.substring(0, t.length() - 2) + "xx")).isEmpty();
    assertThat(tokens.verify("no-dot")).isEmpty();
    assertThat(tokens.verify("a.b.c")).isEmpty();
    assertThat(tokens.verify("%%%.%%%")).isEmpty();
    assertThat(tokens.verify(null)).isEmpty();
  }

  @Test
  void aBoundTokenVerifiesOnlyWithTheSameBinding() {
    String t = tokens.sign("lecture.user.123", "203.0.113.7");

    assertThat(tokens.verify(t, "203.0.113.7")).contains("lecture.user.123");
    assertThat(tokens.verify(t, "198.51.100.9")).isEmpty(); // replayed from another network
    assertThat(tokens.verify(t)).isEmpty(); // unbound verification can't skip the binding
    assertThat(tokens.verify(t, null)).isEmpty();
    assertThat(t).doesNotContain("203.0.113.7"); // ⭐ signed, never carried
  }

  @Test
  void anUnboundTokenIsUnchangedByTheBindingFeature() {
    // tokens issued before bindings existed (unsubscribe links in sent emails) must still verify
    String unbound = tokens.sign("a.b.123");
    assertThat(unbound).isEqualTo(tokens.sign("a.b.123", ""));
    assertThat(tokens.verify(unbound, "anything")).isEmpty();
  }

  @Test
  void aShortSecretIsRefused() {
    assertThatThrownBy(() -> new SignedTokens("short", "the test secret"))
        .hasMessageContaining("the test secret needs at least 32 bytes");
  }
}
