package com.masternova.api.catalog.domain;

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

  /** For tests that commit (@SpringBootTest): courses first, because of the foreign key. */
  public void deleteAll() {
    jdbc.sql("DELETE FROM course").update();
    jdbc.sql("DELETE FROM app_user WHERE email LIKE :pattern")
        .param("pattern", "%" + EMAIL_DOMAIN)
        .update();
  }
}
