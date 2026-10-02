package com.masternova.api.learning.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "learning_section")
class LearningSection {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // ⭐ Always make @ManyToOne LAZY explicitly — its default is EAGER (a classic source of
  //    surprise queries). This side OWNS the relationship: it holds the course_id column.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_id")
  private LearningCourse course;

  private String title;

  private int position;

  protected LearningSection() {}

  LearningSection(LearningCourse course, String title, int position) {
    this.course = course;
    this.title = title;
    this.position = position;
  }

  String title() {
    return title;
  }
}
