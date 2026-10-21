package com.masternova.api.catalog.domain;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Courses reference {@code app_user} (a foreign key), so an integration test needs real user rows.
 * Inserted with plain SQL: catalog's tests shouldn't depend on identity's internals.
 *
 * <p>⭐ {@code JdbcClient.create(dataSource)} joins the test's transaction when there is one
 * (Spring's {@code DataSourceUtils}), so in a {@code @DataJpaTest} the rows roll back with
 * everything else.
 */
public final class TestInstructors {

  public static final String EMAIL_DOMAIN = "@catalog.test";

  private final JdbcClient jdbc;

  public TestInstructors(DataSource dataSource) {
    this.jdbc = JdbcClient.create(dataSource);
  }

  public Instructor create(String name) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO app_user (id, email, display_name, password_hash, created_at, version)"
                + " VALUES (:id, :email, :name, '{noop}x', now(), 0)")
        .param("id", id)
        .param("email", id + EMAIL_DOMAIN)
        .param("name", name)
        .update();
    return new Instructor(id, name);
  }

  /**
   * ⭐ Module listeners (Phase 10's enrollment) run AFTER the test's commit, on their own threads.
   * Deleting their publication rows doesn't stop one already running: a refund's two revocations
   * were still updating {@code enrollment} while {@code DELETE FROM course} cascaded into the same
   * rows in another order, and Postgres reported a deadlock. Wait until none is in flight
   * (completion-mode: delete, so a row is work not yet done; a FAILED one won't be retried here).
   */
  private void awaitListeners() {
    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                jdbc.sql("SELECT count(*) FROM event_publication WHERE status <> 'FAILED'")
                    .query(Long.class)
                    .single(),
            n -> n == 0);
  }

  /** For tests that commit (@SpringBootTest): courses first, because of the foreign key. */
  public void deleteAll() {
    awaitListeners();
    jdbc.sql("DELETE FROM entitlement").update(); // Phase 8: rows reference courses and users
    jdbc.sql("DELETE FROM event_publication").update(); // Phase 10: the FAILED ones left
    jdbc.sql("DELETE FROM purchase_order").update(); // Phase 9: orders (and lines) cite courses
    jdbc.sql("DELETE FROM coupon").update(); // and coupons; a coupon may name a course
    jdbc.sql("DELETE FROM course").update();
    // media assets (Phase 7) reference their owners: remove them first, jobs and sessions with them
    String mine =
        "SELECT a.id FROM media_asset a JOIN app_user u ON u.id = a.owner_id WHERE u.email LIKE :pattern";
    for (String table : java.util.List.of("pipeline_job", "upload_session", "media_rendition")) {
      jdbc.sql("DELETE FROM " + table + " WHERE asset_id IN (" + mine + ")")
          .param("pattern", "%" + EMAIL_DOMAIN)
          .update();
    }
    jdbc.sql("DELETE FROM media_asset WHERE id IN (" + mine + ")")
        .param("pattern", "%" + EMAIL_DOMAIN)
        .update();
    jdbc.sql("DELETE FROM app_user WHERE email LIKE :pattern")
        .param("pattern", "%" + EMAIL_DOMAIN)
        .update();
  }
}
