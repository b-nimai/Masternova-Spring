package com.masternova.api.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.api.catalog.domain.CurriculumCommand.AddLecture;
import com.masternova.api.catalog.domain.CurriculumCommand.RestoreSection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The commands' JSON contract (API conventions §6): what the client sends, and what the edit
 * history stores — every command, its inverse included, must survive a round trip through JSON.
 */
class CurriculumCommandJsonTest {

  final JsonMapper json = JsonMapper.builder().build();

  @Test
  void aClientCommandIsReadByItsKind() {
    UUID section = UUID.randomUUID();

    CurriculumCommand command =
        json.readValue(
            """
            {"kind":"ADD_LECTURE","sectionId":"%s","title":"Q&A","lectureKind":"ARTICLE","durationSeconds":120}
            """
                .formatted(section),
            CurriculumCommand.class);

    assertThat(command)
        .isEqualTo(new AddLecture(null, section, "Q&A", LectureKind.ARTICLE, false, 120));
  }

  @Test
  void anInverseCarryingAMementoRoundTripsThroughJson() {
    CurriculumCommand restore =
        new RestoreSection(
            new SectionSnapshot(
                UUID.randomUUID(),
                "Core",
                List.of(
                    new LectureSnapshot(
                        UUID.randomUUID(),
                        "Pods",
                        LectureKind.VIDEO,
                        true,
                        600,
                        UUID.randomUUID()))),
            1);

    String stored = json.writeValueAsString(restore);

    assertThat(stored).contains("\"kind\":\"RESTORE_SECTION\"");
    assertThat(json.readValue(stored, CurriculumCommand.class)).isEqualTo(restore);
  }

  @Test
  void anUnknownKindIsRejected() {
    assertThatThrownBy(() -> json.readValue("{\"kind\":\"DROP_TABLE\"}", CurriculumCommand.class))
        .isInstanceOf(Exception.class);
  }
}
