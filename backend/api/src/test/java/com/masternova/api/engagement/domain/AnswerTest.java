package com.masternova.api.engagement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.platform.RuleViolationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnswerTest {

  private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
  private static final UUID ME = UUID.randomUUID();

  private final Question question =
      Question.ask(UUID.randomUUID(), UUID.randomUUID(), ME, "Me", "Why?", "", NOW);

  @Test
  void repliesGoOneLevelDeeperUpToThree() {
    Answer answer = Answer.to(question, ME, "Me", "Because.", NOW);
    Answer reply = answer.reply(ME, "Me", "Why because?", NOW);
    Answer replyToReply = reply.reply(ME, "Me", "Just because.", NOW);

    assertThat(answer.depth()).isEqualTo(1);
    assertThat(reply.depth()).isEqualTo(2);
    assertThat(reply.parentId()).isEqualTo(answer.id());
    assertThat(replyToReply.depth()).isEqualTo(3);
    assertThatThrownBy(() -> replyToReply.reply(ME, "Me", "deeper", NOW))
        .isInstanceOf(RuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "REPLY_TOO_DEEP");
  }

  @Test
  void depthAndParentAgree() {
    assertThatThrownBy(
            () -> new Answer(UUID.randomUUID(), question.id(), null, 2, ME, "Me", "x", NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Answer(
                    UUID.randomUUID(), question.id(), UUID.randomUUID(), 1, ME, "Me", "x", NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anAnswerHasWordsAndAQuestionATitle() {
    assertThatThrownBy(() -> Answer.to(question, ME, "Me", "   ", NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> Question.ask(UUID.randomUUID(), UUID.randomUUID(), ME, "Me", " ", "", NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
