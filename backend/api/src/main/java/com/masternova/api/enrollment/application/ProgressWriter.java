package com.masternova.api.enrollment.application;

import com.masternova.api.catalog.CatalogApi;
import com.masternova.api.catalog.LectureOutline;
import com.masternova.api.enrollment.domain.Completion;
import com.masternova.api.enrollment.domain.EnrollmentRepository;
import com.masternova.api.enrollment.domain.LectureProgressRepository;
import com.masternova.api.enrollment.domain.ProgressStore;
import com.masternova.api.enrollment.domain.ProgressUpdate;
import com.masternova.api.enrollment.domain.TrackedProgress;
import com.masternova.api.enrollment.domain.UserCourse;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Progress written straight to Postgres — the ConcreteComponent under the write-back buffer
 * (ADR-0022): the flush hands it a batch every 30 s, and a heartbeat comes here directly only when
 * Redis is down.
 *
 * <p>Validation happens HERE, once per batch, not per heartbeat: one catalog query for every
 * lecture in it (unknown ids dropped), one enrollment query for every pair (non-enrolled learners
 * dropped: previews, free courses watched without enrolling, forged ids), then one upsert and one
 * rollup recount. Four statements for any batch size, in one transaction.
 *
 * <p>Not a bean: only its decorator is (EnrollmentConfig), so nothing injects the unbuffered store
 * by accident. Hence a {@link TransactionOperations} instead of {@code @Transactional}, which needs
 * a proxy.
 */
@DesignPattern(
    value = Pattern.DECORATOR,
    role = "ConcreteComponent: validated write-through to Postgres",
    note = "patterns/docs/13-decorator.md")
public class ProgressWriter implements ProgressStore {

  private final CatalogApi catalog;
  private final EnrollmentRepository enrollments;
  private final LectureProgressRepository progress;
  private final TransactionOperations tx;
  private final MeterRegistry meters;

  public ProgressWriter(
      CatalogApi catalog,
      EnrollmentRepository enrollments,
      LectureProgressRepository progress,
      TransactionOperations tx,
      MeterRegistry meters) {
    this.catalog = catalog;
    this.enrollments = enrollments;
    this.progress = progress;
    this.tx = tx;
    this.meters = meters;
  }

  @Override
  public void record(ProgressUpdate update) {
    recordAll(List.of(update));
  }

  @Override
  public void recordAll(Collection<ProgressUpdate> updates) {
    if (!updates.isEmpty()) {
      tx.executeWithoutResult(status -> write(updates));
    }
  }

  private void write(Collection<ProgressUpdate> updates) {
    List<ProgressUpdate> rows = ProgressUpdate.mergeByRow(updates); // an upsert touches a row once
    Map<UUID, LectureOutline> lectures =
        catalog.lectures(rows.stream().map(ProgressUpdate::lectureId).distinct().toList()).stream()
            .collect(Collectors.toMap(LectureOutline::lectureId, Function.identity()));

    List<TrackedProgress> known = new ArrayList<>();
    for (ProgressUpdate row : rows) {
      LectureOutline lecture = lectures.get(row.lectureId());
      if (lecture == null) {
        dropped("unknown_lecture", 1);
      } else {
        // only a trackable lecture can be completed: an article's estimate is no length to watch
        int duration =
            Completion.isTrackable(lecture.video(), lecture.durationSeconds())
                ? lecture.durationSeconds()
                : 0;
        known.add(new TrackedProgress(row, lecture.courseId(), duration));
      }
    }
    Set<UserCourse> enrolled =
        enrollments.activeAmong(known.stream().map(ProgressWriter::pair).distinct().toList());
    List<TrackedProgress> tracked = known.stream().filter(t -> enrolled.contains(pair(t))).toList();
    dropped("not_enrolled", known.size() - tracked.size());

    progress.upsert(tracked);
    enrollments.refreshRollups(tracked.stream().map(ProgressWriter::pair).distinct().toList());
  }

  @Override
  public Map<UUID, ProgressUpdate> find(UUID userId, Collection<UUID> lectureIds) {
    return progress.find(userId, lectureIds);
  }

  private static UserCourse pair(TrackedProgress t) {
    return new UserCourse(t.update().userId(), t.courseId());
  }

  private void dropped(String reason, int count) {
    if (count > 0) {
      meters.counter("enrollment.progress.dropped", "reason", reason).increment(count);
    }
  }
}
