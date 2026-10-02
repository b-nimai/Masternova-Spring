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
  @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
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
    this.id = UUID.randomUUID();
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
