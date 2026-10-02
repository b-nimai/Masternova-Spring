package com.masternova.api.learning.jpa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;

/**
 * A JPA ENTITY — note what it is NOT: not a record, not final (Hibernate subclasses it for lazy
 * proxies), and it has a no-arg constructor (Hibernate instantiates it reflectively).
 */
@Entity
@Table(name = "learning_course")
class LearningCourse {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String title;

  @Version // optimistic locking column — Phase 6 turns a version clash into a 409
  private long version;

  // ⭐ One course → many sections. LAZY by default for collections: sections are loaded only when
  //    first touched. mappedBy = the FIELD on the other side that owns the foreign key.
  @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position")
  private List<LearningSection> sections = new ArrayList<>();

  protected LearningCourse() {} // for Hibernate

  LearningCourse(String title) {
    this.title = title;
  }

  /** ⭐ Keep BOTH sides of a bidirectional association in sync — in one place. */
  void addSection(String sectionTitle) {
    sections.add(new LearningSection(this, sectionTitle, sections.size()));
  }

  Long id() {
    return id;
  }

  String title() {
    return title;
  }

  void rename(String newTitle) {
    this.title = newTitle;
  }

  long version() {
    return version;
  }

  List<LearningSection> sections() {
    return sections;
  }
}
