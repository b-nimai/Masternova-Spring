package com.masternova.api.platform.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.entitlement.EntitlementApi;
import com.masternova.api.entitlement.EntitlementSource;
import com.masternova.api.entitlement.TestCourses;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * ADR-0021's promise: a module listener that fails is not lost. The enrollment listener is made to
 * fail by a trigger (a bug, a constraint, a dependency down), its publication stays FAILED, and
 * once the cause is gone the resubmitter delivers it.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FailedPublicationResubmitterIT {

  @Autowired DataSource dataSource;
  @Autowired EntitlementApi entitlements;
  @Autowired FailedPublicationResubmitter resubmitter;
  JdbcClient jdbc;
  UUID learner;
  UUID course;

  @BeforeEach
  void setUp() {
    jdbc = JdbcClient.create(dataSource);
    TestInstructors people = new TestInstructors(dataSource);
    UUID instructor = people.create("Asha Rao").id();
    learner = people.create("Lena Learner").id();
    course =
        new TestCourses(dataSource).create(instructor, "PUBLISHED", 49_900, true, null).courseId();
    jdbc.sql(
            """
            CREATE FUNCTION enrollment_down() RETURNS trigger AS $$
            BEGIN RAISE EXCEPTION 'enrollment is down'; END $$ LANGUAGE plpgsql
            """)
        .update();
    jdbc.sql(
            """
            CREATE TRIGGER enrollment_down BEFORE INSERT ON enrollment
            FOR EACH ROW EXECUTE FUNCTION enrollment_down()
            """)
        .update();
  }

  @AfterEach
  void clean() {
    jdbc.sql("DROP TRIGGER IF EXISTS enrollment_down ON enrollment").update();
    jdbc.sql("DROP FUNCTION IF EXISTS enrollment_down()").update();
    new TestInstructors(dataSource).deleteAll();
  }

  @Test
  void aFailedListenerIsDeliveredOnceTheCauseIsGone() {
    entitlements.grant(learner, course, EntitlementSource.PURCHASE, Optional.empty());

    // the grant committed (access is synchronous, ADR-0019); its enrollment failed and is KEPT
    await().atMost(Duration.ofSeconds(10)).until(() -> failed() == 1);
    assertThat(enrolled()).isZero();

    jdbc.sql("DROP TRIGGER enrollment_down ON enrollment").update(); // the fix is deployed
    resubmitter.resubmit(); // too young: a failure that might be handled right now is left alone
    assertThat(failed()).isEqualTo(1);

    jdbc.sql("UPDATE event_publication SET publication_date = now() - interval '2 minutes'")
        .update();
    resubmitter.resubmit();

    await().atMost(Duration.ofSeconds(10)).until(() -> enrolled() == 1);
    await().atMost(Duration.ofSeconds(10)).until(() -> publications() == 0); // completed, deleted
  }

  @Test
  void aPoisonEventIsGivenUpAfterTenAttempts() {
    entitlements.grant(learner, course, EntitlementSource.PURCHASE, Optional.empty());
    await().atMost(Duration.ofSeconds(10)).until(() -> failed() == 1);
    jdbc.sql(
            """
            UPDATE event_publication SET publication_date = now() - interval '2 minutes',
                                         completion_attempts = :max
            """)
        .param("max", FailedPublicationResubmitter.MAX_ATTEMPTS)
        .update();

    resubmitter.resubmit();

    assertThat(failed()).isEqualTo(1); // left for a human, visible in events.publications.failed
  }

  private long failed() {
    return jdbc.sql("SELECT count(*) FROM event_publication WHERE status = 'FAILED'")
        .query(Long.class)
        .single();
  }

  private long publications() {
    return jdbc.sql("SELECT count(*) FROM event_publication").query(Long.class).single();
  }

  private long enrolled() {
    return jdbc.sql("SELECT count(*) FROM enrollment WHERE user_id = :u AND status = 'ACTIVE'")
        .param("u", learner)
        .query(Long.class)
        .single();
  }
}
