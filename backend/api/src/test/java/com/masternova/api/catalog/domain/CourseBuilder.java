package com.masternova.api.catalog.domain;

import com.masternova.kernel.money.Money;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A TEST DATA BUILDER for the Course aggregate (Nat Pryce's variant of Builder): every field has a
 * safe default, and a test states ONLY what it's about.
 *
 * <pre>{@code
 * Course k8s = aCourse().slug("k8s").in(devops).priced(149900).published(T0)
 *     .withSection("Intro", aLecture("Welcome").preview().seconds(90))
 *     .build();
 * }</pre>
 *
 * <p>⭐ The force: {@code Course.draft(...)} takes nine arguments, and most tests care about one or
 * two. Without a builder every test repeats all nine (and a new required field breaks every test
 * that builds a course); with one, the defaults live HERE, once. The builder goes through the real
 * factory and methods ({@code draft}, {@code addSection}, {@code addLecture}, {@code publish}), so
 * every built course obeys the aggregate's invariants — a builder that set fields by reflection
 * could build courses that can't exist.
 *
 * <p>Pattern note: patterns/docs/10-builder.md.
 */
@DesignPattern(
    value = Pattern.BUILDER,
    role = "Builder (test data)",
    note = "patterns/docs/10-builder.md")
public final class CourseBuilder {

  private static final AtomicInteger SEQUENCE = new AtomicInteger();
  public static final Instant DEFAULT_TIME = Instant.parse("2026-09-01T10:00:00Z");

  // ⭐ defaults: valid, boring, and unique where the schema demands uniqueness (slug)
  private String slug = "course-" + SEQUENCE.incrementAndGet();
  private String title;
  private String subtitle;
  private String description =
      "A course built by a test: long enough to pass the publish gate's description rule.";
  private CourseLevel level = CourseLevel.BEGINNER;
  private String language = "en";
  private Money price = Money.zero("INR");
  private boolean priceConfirmed = true; // a decided price is the boring default
  private Category category;
  private Instructor instructor = new Instructor(UUID.randomUUID(), "Test Instructor");
  private Instant createdAt = DEFAULT_TIME;
  private Instant publishedAt; // null = not published
  private boolean submitted; // IN_REVIEW
  private BigDecimal ratingAverage;
  private int ratingCount;
  private final List<SectionSpec> sections = new ArrayList<>();

  private CourseBuilder() {}

  public static CourseBuilder aCourse() {
    return new CourseBuilder();
  }

  public static LectureBuilder aLecture(String title) {
    return new LectureBuilder(title);
  }

  // ------------------------------------------------------------------ fluent setters

  public CourseBuilder slug(String slug) {
    this.slug = slug;
    return this;
  }

  public CourseBuilder title(String title) {
    this.title = title;
    return this;
  }

  public CourseBuilder subtitle(String subtitle) {
    this.subtitle = subtitle;
    return this;
  }

  public CourseBuilder description(String description) {
    this.description = description;
    return this;
  }

  public CourseBuilder level(CourseLevel level) {
    this.level = level;
    return this;
  }

  public CourseBuilder language(String language) {
    this.language = language;
    return this;
  }

  public CourseBuilder priced(long amountMinor) {
    this.price = Money.of(amountMinor, "INR");
    return this;
  }

  public CourseBuilder free() {
    return priced(0);
  }

  /** Pricing not decided yet: the publish gate's PRICE_NOT_SET. */
  public CourseBuilder unpriced() {
    this.priceConfirmed = false;
    return this;
  }

  public CourseBuilder in(Category category) {
    this.category = category;
    return this;
  }

  public CourseBuilder by(Instructor instructor) {
    this.instructor = instructor;
    return this;
  }

  public CourseBuilder createdAt(Instant createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public CourseBuilder published(Instant at) {
    this.publishedAt = at;
    return this;
  }

  public CourseBuilder published() {
    return published(createdAt);
  }

  /** Submitted for review (IN_REVIEW), through the real transition. */
  public CourseBuilder submitted() {
    this.submitted = true;
    return this;
  }

  public CourseBuilder rated(String average, int count) {
    this.ratingAverage = new BigDecimal(average);
    this.ratingCount = count;
    return this;
  }

  public CourseBuilder withSection(String title, LectureBuilder... lectures) {
    sections.add(new SectionSpec(title, List.of(lectures)));
    return this;
  }

  /** A generated curriculum: {@code sections × lecturesEach} one-minute video lectures. */
  public CourseBuilder withCurriculum(int sectionCount, int lecturesEach) {
    for (int s = 0; s < sectionCount; s++) {
      List<LectureBuilder> lectures = new ArrayList<>();
      for (int l = 0; l < lecturesEach; l++) {
        lectures.add(aLecture("Lecture " + s + "." + l).seconds(60));
      }
      sections.add(new SectionSpec("Section " + s, lectures));
    }
    return this;
  }

  // ------------------------------------------------------------------ build

  /** ⭐ Through the REAL factory and methods: a built course is always a legal course. */
  public Course build() {
    Course course =
        Course.draft(
            slug,
            title != null ? title : "Course " + slug,
            description,
            level,
            language,
            price,
            category != null ? category : new Category(), // unit tests don't need a real one
            instructor,
            createdAt);
    course.changeSubtitle(subtitle);
    if (priceConfirmed) {
      course.confirmPrice(price, createdAt);
    }
    for (SectionSpec spec : sections) {
      Section section = course.addSection(spec.title());
      spec.lectures().forEach(lecture -> lecture.addTo(course, section));
    }
    if (ratingAverage != null) {
      course.updateRatingSummary(ratingAverage, ratingCount);
    }
    if (submitted || publishedAt != null) {
      Instant at = publishedAt != null ? publishedAt : createdAt;
      if (sections.isEmpty()) {
        addMinimalCurriculum(course); // a course can only reach review with a real curriculum
      }
      // ⭐ through the REAL lifecycle: DRAFT → IN_REVIEW → PUBLISHED, gate included. A test that
      //    asks for a published course with an incomplete curriculum fails loudly here.
      course.transition(CourseAction.SUBMIT, at);
      if (publishedAt != null) {
        course.transition(CourseAction.PUBLISH, at);
      }
    }
    return course;
  }

  /** The smallest curriculum the publish gate accepts: 3 lectures, one of them a preview. */
  private static void addMinimalCurriculum(Course course) {
    Section section = course.addSection("Getting started");
    course.addLecture(
        section, "Welcome", LectureKind.VIDEO, true, LectureDuration.ofSeconds(60), null);
    course.addLecture(
        section, "Setup", LectureKind.VIDEO, false, LectureDuration.ofSeconds(60), null);
    course.addLecture(section, "Notes", LectureKind.ARTICLE, false, LectureDuration.ZERO, null);
  }

  private record SectionSpec(String title, List<LectureBuilder> lectures) {}

  /** The nested builder for one lecture. */
  public static final class LectureBuilder {
    private final String title;
    private LectureKind kind = LectureKind.VIDEO;
    private boolean preview;
    private int seconds = 60;
    private UUID assetId;

    private LectureBuilder(String title) {
      this.title = title;
    }

    public LectureBuilder article() {
      this.kind = LectureKind.ARTICLE;
      this.seconds = 0;
      return this;
    }

    public LectureBuilder preview() {
      this.preview = true;
      return this;
    }

    public LectureBuilder seconds(int seconds) {
      this.seconds = seconds;
      return this;
    }

    public LectureBuilder asset(UUID assetId) {
      this.assetId = assetId;
      return this;
    }

    private void addTo(Course course, Section section) {
      course.addLecture(section, title, kind, preview, LectureDuration.ofSeconds(seconds), assetId);
    }
  }
}
