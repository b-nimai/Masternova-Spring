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
  void aShortSecretIsRefused() {
    assertThatThrownBy(() -> new SignedTokens("short", "the test secret"))
        .hasMessageContaining("the test secret needs at least 32 bytes");
  }
}
