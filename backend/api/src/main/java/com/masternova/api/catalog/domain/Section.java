package com.masternova.api.catalog.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

/** A section of a course — an entity INSIDE the Course aggregate (no repository of its own). */
@Entity
@Table(name = "section")
public class Section {

  @Id private UUID id;

  // ⭐ The OWNING side of the relationship: this field's join column (course_id) is what JPA
  //    writes. Course.sections is the INVERSE side (mappedBy) — changing only that list would
  //    write nothing.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_id")
  private Course course;

  @Column(nullable = false, length = 120)
  private String title;

  @Column(nullable = false)
  private int position;

  // ⭐ @BatchSize: when ONE section's lectures are touched, Hibernate loads the lectures of up to
  //    64 sections in that persistence context with ONE "WHERE section_id IN (…)" query. That turns
  //    the curriculum's N+1 (a query per section) into 1. Note 12 §5.
  //
  // ⭐ NO orphanRemoval here (on purpose, found by CurriculumIT): moving a lecture to another
  //    section takes it out of THIS list, and orphanRemoval would DELETE it at flush even though it
  //    was just added to the other section (JPA calls re-parenting an orphan non-portable). An
  //    explicit removal goes through Course.removeLecture → CurriculumCleanup instead. Removing a
  //    whole section still deletes its lectures: cascade = ALL includes REMOVE.
  @OneToMany(mappedBy = "section", cascade = CascadeType.ALL)
  @OrderBy("position")
  @BatchSize(size = 64)
  private List<Lecture> lectures = new ArrayList<>();

  protected Section() {} // for Hibernate

  /** ⭐ PROTOTYPE copy constructor: a new section in {@code course}, its lectures copied DEEPLY. */
  Section(Course course, Section source) {
    this.id = UUID.randomUUID();
    this.course = course;
    this.title = source.title;
    this.position = source.position;
    // ⭐ a NEW list of NEW lectures: sharing source.lectures would make the two courses edit
    //    one curriculum (and confuse Hibernate: one collection, two owners)
    source.lectures.forEach(lecture -> this.lectures.add(new Lecture(this, lecture)));
  }

  Section(Course course, String title, int position) {
    this(UUID.randomUUID(), course, title, position);
  }

  Section(UUID id, Course course, String title, int position) {
    this.id = Objects.requireNonNull(id, "id");
    this.course = course;
    this.title = Lecture.requireTitle(title);
    this.position = position;
  }

  Lecture addLecture(
      String title, LectureKind kind, boolean preview, LectureDuration duration, UUID assetId) {
    Lecture lecture = new Lecture(this, title, kind, lectures.size(), preview, duration, assetId);
    lectures.add(lecture);
    return lecture;
  }

  // ------------------------------------------------------------------ curriculum operations
  // Package-private: only Course (the root) calls them, and it keeps the rollups and version right.

  void rename(String newTitle) {
    title = Lecture.requireTitle(newTitle);
  }

  void placeAt(int newPosition) {
    position = newPosition;
  }

  java.util.Optional<Lecture> lecture(UUID lectureId) {
    return lectures.stream().filter(l -> l.id().equals(lectureId)).findFirst();
  }

  int indexOf(Lecture lecture) {
    return lectures.indexOf(lecture);
  }

  /** Inserts at {@code index} (clamped into range) and renumbers. */
  void insert(Lecture lecture, int index) {
    lectures.add(Math.clamp(index, 0, lectures.size()), lecture);
    renumber();
  }

  /** Takes a lecture out of this section's list (MOVE or REMOVE) and renumbers the rest. */
  void detach(Lecture lecture) {
    lectures.remove(lecture);
    renumber();
  }

  /**
   * ⭐ Positions are always 0..n-1 in list order. Renumbering row by row makes two rows share a
   * position for a moment; the UNIQUE constraint is DEFERRABLE INITIALLY DEFERRED (V11), so it's
   * checked at commit, when every row has its final position.
   */
  private void renumber() {
    for (int i = 0; i < lectures.size(); i++) {
      lectures.get(i).placeIn(this, i);
    }
  }

  SectionSnapshot snapshot() {
    return new SectionSnapshot(id, title, lectures.stream().map(Lecture::snapshot).toList());
  }

  public UUID id() {
    return id;
  }

  public Course course() {
    return course;
  }

  public String title() {
    return title;
  }

  public int position() {
    return position;
  }

  /** Read-only: lectures change only through the Course, which keeps the rollups right. */
  public List<Lecture> lectures() {
    return Collections.unmodifiableList(lectures);
  }
}
