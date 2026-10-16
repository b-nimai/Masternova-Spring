package com.masternova.api.enrollment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.masternova.api.TestcontainersConfiguration;
import com.masternova.api.catalog.domain.TestInstructors;
import com.masternova.api.enrollment.application.EnrollmentService;
import com.masternova.api.enrollment.domain.ProgressUpdate;
import com.masternova.api.entitlement.EntitlementSource;
import com.masternova.api.entitlement.TestCourses;
import com.masternova.api.entitlement.TestCourses.Seeded;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * ⭐ The write-back buffer against real Redis and Postgres (ADR-0022): a flush that dies halfway
 * loses nothing and counts nothing twice; heartbeats during a flush land in the next one; and the
 * three copies of the merge — Java, Lua, SQL — agree. The scheduled flush is off: tests flush by
 * hand.
 */
@SpringBootTest(properties = "masternova.enrollment.flush-enabled=false")
@Import(TestcontainersConfiguration.class)
class WriteBackProgressStoreIT {

  static final Instant T0 = Instant.parse("2026-10-16T10:00:00Z");

  @Autowired WriteBackProgressStore store;
  @Autowired StringRedisTemplate redis;
  @Autowired EnrollmentService enrollments;
  @Autowired DataSource dataSource;
  JdbcClient jdbc;
  UUID learner;
  Seeded course; // two 60 s videos

  @BeforeEach
  void setUp() {
    jdbc = JdbcClient.create(dataSource);
    clearBuffer();
    TestInstructors people = new TestInstructors(dataSource);
    UUID instructor = people.create("Asha Rao").id();
    learner = people.create("Lena Learner").id();
    course = new TestCourses(dataSource).create(instructor, "PUBLISHED", 49_900, true, null);
    enrollments.granted(learner, course.courseId(), EntitlementSource.PURCHASE, T0);
  }

  @AfterEach
  void clean() {
    clearBuffer();
    new TestInstructors(dataSource).deleteAll();
  }

  @Test
  void aHeartbeatIsReadBackAtOnceAndReachesPostgresOnlyAtTheFlush() {
    store.record(beat(course.paidLecture(), 42, T0));

    assertThat(store.find(learner, List.of(course.paidLecture())).get(course.paidLecture()))
        .extracting(ProgressUpdate::lastPositionSeconds)
        .isEqualTo(42); // resume reads through the buffer
    assertThat(rows()).isZero(); // no SQL on the heartbeat path

    assertThat(store.flush()).isEqualTo(1);
    assertThat(rows()).isEqualTo(1);
    assertThat(redis.hasKey(WriteBackProgressStore.DIRTY)).isFalse();
    assertThat(redis.hasKey(WriteBackProgressStore.FLUSHING)).isFalse();
  }

  @Test
  void aFlushThatDiedAfterTheRenameIsFinishedByTheNextAndHeartbeatsSinceWait() {
    store.record(beat(course.previewLecture(), 60, T0)); // complete (≥ 90 % of 60 s)
    store.record(beat(course.paidLecture(), 20, T0));
    // the crash: the batch was moved aside, then the replica died
    redis.execute(
        WriteBackProgressStore.SWAP,
        List.of(WriteBackProgressStore.DIRTY, WriteBackProgressStore.FLUSHING));
    store.record(beat(course.paidLecture(), 35, T0.plusSeconds(15))); // a new dirty hash

    assertThat(store.find(learner, List.of(course.paidLecture())).get(course.paidLecture()))
        .extracting(ProgressUpdate::lastPositionSeconds)
        .isEqualTo(35); // both hashes are read while the flush is pending

    assertThat(store.flush()).isEqualTo(3); // the leftover first, then the new batch
    assertThat(position(course.paidLecture())).isEqualTo(35);
    assertThat(completedLectures()).isEqualTo(1);
  }

  @Test
  void aBatchWrittenTwiceChangesNothing() {
    store.record(beat(course.previewLecture(), 58, T0));
    store.record(beat(course.paidLecture(), 30, T0));
    Map<Object, Object> batch = redis.opsForHash().entries(WriteBackProgressStore.DIRTY);
    store.flush();
    Map<String, Object> before = row(course.paidLecture());

    // the crash: every row committed, the replica died before DEL — the batch is still there
    redis.opsForHash().putAll(WriteBackProgressStore.FLUSHING, batch);
    store.flush();

    assertThat(row(course.paidLecture())).isEqualTo(before);
    assertThat(completedLectures()).isEqualTo(1); // a recount, not +1
  }

  @Test
  void javaLuaAndSqlMergeTheSameSequencesToTheSameRow() {
    for (int seed = 0; seed < 25; seed++) {
      clearBuffer();
      jdbc.sql("DELETE FROM lecture_progress").update();
      Random random = new Random(seed);
      List<ProgressUpdate> beats = new ArrayList<>();
      for (int i = 0; i < 1 + random.nextInt(12); i++) {
        int position = random.nextInt(120);
        beats.add(
            new ProgressUpdate(
                learner,
                course.paidLecture(),
                position,
                position + random.nextInt(30),
                T0.plusMillis(random.nextInt(5) * 15_000L)));
      }
      ProgressUpdate java = beats.stream().reduce(ProgressUpdate::merge).orElseThrow();

      beats.forEach(store::record); // Lua, in this order
      ProgressUpdate lua =
          WriteBackProgressStore.parse(
                  learner,
                  course.paidLecture(),
                  redis
                      .opsForHash()
                      .get(
                          WriteBackProgressStore.DIRTY,
                          WriteBackProgressStore.field(learner, course.paidLecture())))
              .orElseThrow();
      clearBuffer();

      List<ProgressUpdate> shuffled = new ArrayList<>(beats);
      Collections.shuffle(shuffled, random);
      for (ProgressUpdate beat : shuffled) { // SQL: one flush per heartbeat, another order
        store.record(beat);
        store.flush();
      }
      ProgressUpdate sql =
          store.find(learner, List.of(course.paidLecture())).get(course.paidLecture());

      assertThat(lua).as("Lua, seed %d", seed).isEqualTo(java);
      assertThat(sql).as("SQL, seed %d", seed).isEqualTo(java);
    }
  }

  @Test
  void progressOfAnUnknownLectureOrAnUnenrolledLearnerIsDroppedAtTheFlush() {
    UUID stranger = new TestInstructors(dataSource).create("Sam Stranger").id();
    store.record(beat(UUID.randomUUID(), 10, T0)); // a forged lecture id
    store.record(ProgressUpdate.heartbeat(stranger, course.paidLecture(), 10, T0)); // not enrolled

    store.flush();

    assertThat(rows()).isZero();
    assertThat(redis.hasKey(WriteBackProgressStore.FLUSHING)).isFalse(); // dropped, not stuck
  }

  // --- helpers ---

  private ProgressUpdate beat(UUID lecture, int position, Instant at) {
    return ProgressUpdate.heartbeat(learner, lecture, position, at);
  }

  private void clearBuffer() {
    redis.delete(List.of(WriteBackProgressStore.DIRTY, WriteBackProgressStore.FLUSHING));
  }

  private long rows() {
    return jdbc.sql("SELECT count(*) FROM lecture_progress").query(Long.class).single();
  }

  private int position(UUID lecture) {
    return (int) row(lecture).get("last_position_seconds");
  }

  private Map<String, Object> row(UUID lecture) {
    return jdbc.sql("SELECT * FROM lecture_progress WHERE user_id = :u AND lecture_id = :l")
        .param("u", learner)
        .param("l", lecture)
        .query()
        .singleRow();
  }

  private int completedLectures() {
    return jdbc.sql(
            "SELECT completed_lectures FROM enrollment WHERE user_id = :u AND course_id = :c")
        .param("u", learner)
        .param("c", course.courseId())
        .query(Integer.class)
        .single();
  }
}
