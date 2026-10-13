package com.masternova.api.media.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.Instructor;
import com.masternova.api.catalog.domain.TestInstructors;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The pipeline's dead-letter queue: list, replay once, and who may (video-pipeline LLD §8). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DeadJobReplayIT {

  @Autowired MockMvcTester mvc;
  @Autowired DataSource dataSource;
  JdbcClient jdbc;
  Instructor asha;

  @BeforeEach
  void setUp() {
    jdbc = JdbcClient.create(dataSource);
    clean();
    asha = new TestInstructors(dataSource).create("Asha Rao");
  }

  @AfterEach
  void clean() {
    JdbcClient db = JdbcClient.create(dataSource);
    db.sql("DELETE FROM pipeline_job").update();
    db.sql("DELETE FROM media_asset").update();
    new TestInstructors(dataSource).deleteAll();
  }

  private static RequestPostProcessor as(Role role) {
    return jwt()
        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of(role.name())))
        .authorities(List.<GrantedAuthority>of(new SimpleGrantedAuthority(role.authority())));
  }

  /** A FAILED asset whose {@code type} job died after {@code attempts} tries. */
  private UUID deadJob(String type) {
    UUID asset = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO media_asset (id, owner_id, kind, status, original_filename, content_type,
                                     size_bytes, failure_code, created_at, version)
            VALUES (:id, :owner, 'VIDEO', 'FAILED', 'a.mp4', 'video/mp4', 1, 'TRANSCODE_FAILED', now(), 0)
            """)
        .param("id", asset)
        .param("owner", asha.id())
        .update();
    UUID job = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO pipeline_job (id, asset_id, type, rung, status, attempts, next_attempt_at,
                                      last_error, created_at, updated_at)
            VALUES (:id, :asset, :type, :rung, 'DEAD', 5, now(), 'StorageUnavailableException: 503', now(), now())
            """)
        .param("id", job)
        .param("asset", asset)
        .param("type", type)
        .param("rung", type.equals("TRANSCODE") ? "720p" : "")
        .update();
    return job;
  }

  private MvcTestResult replay(UUID job, Role role) {
    return mvc.post().uri("/api/v1/admin/media/jobs/{id}/replay", job).with(as(role)).exchange();
  }

  private String column(String sql, UUID id) {
    return jdbc.sql(sql).param("id", id).query(String.class).single();
  }

  @Test
  void anAdminSeesWhatDiedAndWhy() {
    UUID job = deadJob("TRANSCODE");

    MvcTestResult result =
        mvc.get().uri("/api/v1/admin/media/jobs/dead").with(as(Role.ADMIN)).exchange();

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$[0].id").isEqualTo(job.toString());
    assertThat(result)
        .bodyJson()
        .extractingPath("$[0].lastError")
        .isEqualTo("StorageUnavailableException: 503");
  }

  @Test
  void replayRequeuesTheJobWithAFreshBudgetAndRevivesTheAsset() {
    UUID job = deadJob("TRANSCODE");

    assertThat(replay(job, Role.ADMIN)).hasStatusOk();

    assertThat(column("SELECT status FROM pipeline_job WHERE id = :id", job)).isEqualTo("PENDING");
    assertThat(column("SELECT attempts::text FROM pipeline_job WHERE id = :id", job))
        .isEqualTo("0");
    UUID asset =
        jdbc.sql("SELECT asset_id FROM pipeline_job WHERE id = :id")
            .param("id", job)
            .query(UUID.class)
            .single();
    assertThat(column("SELECT status FROM media_asset WHERE id = :id", asset))
        .isEqualTo("PROCESSING");
    assertThat(
            jdbc.sql("SELECT failure_code FROM media_asset WHERE id = :id")
                .param("id", asset)
                .query(String.class)
                .optional())
        .isEmpty();
  }

  @Test
  void aReplayedProbeSendsTheAssetBackToUploaded() {
    UUID job = deadJob("PROBE");
    replay(job, Role.ADMIN);
    UUID asset =
        jdbc.sql("SELECT asset_id FROM pipeline_job WHERE id = :id")
            .param("id", job)
            .query(UUID.class)
            .single();
    assertThat(column("SELECT status FROM media_asset WHERE id = :id", asset))
        .isEqualTo("UPLOADED");
  }

  @Test
  void aSecondReplayChangesNothing() {
    UUID job = deadJob("PACKAGE");
    replay(job, Role.ADMIN);

    assertThat(replay(job, Role.ADMIN))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("JOB_NOT_DEAD");
  }

  @Test
  void unknownJobsAre404AndOnlyAdminsMayReplay() {
    assertThat(replay(UUID.randomUUID(), Role.ADMIN)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(replay(deadJob("PROBE"), Role.INSTRUCTOR)).hasStatus(HttpStatus.FORBIDDEN);
  }
}
