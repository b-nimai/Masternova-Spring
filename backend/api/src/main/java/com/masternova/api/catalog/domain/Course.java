package com.masternova.api.catalog.domain;

import com.masternova.api.platform.ConflictException;
import com.masternova.api.platform.RuleViolationException;
import com.masternova.kernel.money.Money;
import com.masternova.kernel.pattern.DesignPattern;
import com.masternova.kernel.pattern.Pattern;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A course — the AGGREGATE ROOT of Course → Section → Lecture (docs/lld/catalog.md §3). Every
 * change to the curriculum goes through this class, which is how the rollups ({@link
 * #lectureCount()}, {@link #totalDuration()}) stay true without a trigger or a recount.
 *
 * <p>JPA rules (note 10 §4): not final, a protected no-arg constructor, {@code @Version}. Mapping
 * choices (note 12): every association LAZY; {@code Money} embedded as two columns; sections
 * ordered by {@code position}.
 */
@Entity
@Table(name = "course")
@DesignPattern(
    value = Pattern.PROTOTYPE,
    role = "ConcretePrototype (duplicateAsDraft)",
    note = "patterns/docs/11-prototype.md")
public class Course {

  private static final java.util.regex.Pattern SLUG =
      java.util.regex.Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
  private static final java.util.regex.Pattern LANGUAGE =
      java.util.regex.Pattern.compile("[a-z]{2}");
  private static final BigDecimal MAX_RATING = BigDecimal.valueOf(5);

  /**
   * ⭐ The storefront prices every course in ONE currency. The price sorts and the free/paid filter
   * compare {@code price_minor} in SQL, and paise and cents aren't comparable (found in review: a
   * $19.99 course sorted below a ₹499 one). Converting needs exchange rates, which isn't the
   * catalog's job. {@code Money} itself stays multi-currency; the DB enforces this too (V9).
   */
  public static final Currency CATALOG_CURRENCY = Currency.getInstance("INR");

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 140)
  private String slug;

  @Column(nullable = false, length = 120)
  private String title;

  @Column(length = 200)
  private String subtitle;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @Column(nullable = false, length = 8)
  private String language;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CourseLevel level;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CourseStatus status;

  @Column(name = "published_at")
  private Instant publishedAt;

  // ⭐ @Embedded value object: two columns of THIS table. @AttributeOverride renames the record's
  //    components (amountMinor, currency) to the column names this table uses.
  @Embedded
  @AttributeOverride(name = "amountMinor", column = @Column(name = "price_minor", nullable = false))
  @AttributeOverride(
      name = "currency",
      column = @Column(name = "currency", nullable = false, length = 3))
  private Money price;

  /**
   * When pricing was confirmed. ⭐ Separate from the price because {@code 0} can't tell "free" from
   * "nobody has decided yet" — and the publish gate needs to know which (PRICE_NOT_SET).
   */
  @Column(name = "price_set_at")
  private Instant priceSetAt;

  @Column(name = "instructor_id", nullable = false)
  private UUID instructorId; // an id, not a @ManyToOne User: identity's entity is not ours to map

  @Column(name = "instructor_name", nullable = false, length = 100)
  private String instructorName;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id")
  private Category category;

  @Column(name = "rating_average", nullable = false, precision = 3, scale = 2)
  private BigDecimal ratingAverage;

  @Column(name = "rating_count", nullable = false)
  private int ratingCount;

  @Column(name = "enrollment_count", nullable = false)
  private int enrollmentCount;

  @Column(name = "lecture_count", nullable = false)
  private int lectureCount;

  @Column(name = "total_duration_seconds", nullable = false)
  private LectureDuration totalDuration;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private Long version; // null = new (Spring Data's isNew check), then 0, 1, 2…

  // ⭐ The INVERSE side (mappedBy): Section.course owns the foreign key. cascade = ALL means saving
  //    the course saves its sections (and, through Section, their lectures); orphanRemoval deletes
  //    a section removed from this list. A List with @OrderBy is a "bag" to Hibernate — see note 12
  //    §4 for why two bags can't be JOIN FETCHed in one query.
  @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position")
  private List<Section> sections = new ArrayList<>();

  protected Course() {} // for Hibernate

  /**
   * ⭐ PROTOTYPE copy constructor (private: {@link #duplicateAsDraft} is the only way in).
   *
   * <ul>
   *   <li>DEEP: sections and lectures are new objects with new ids (each copied by its own copy
   *       constructor — every class copies what it owns).
   *   <li>RESET: status, publishedAt, ratings, enrollments — a copy has no history.
   *   <li>SHARED: immutable values ({@code Money}), references to other aggregates ({@code
   *       Category}), and media asset ids.
   * </ul>
   */
  private Course(Course source, String slug, Instant now) {
    this.id = UUID.randomUUID();
    this.slug = requireSlug(slug);
    this.title = copyTitle(source.title);
    this.subtitle = source.subtitle;
    this.description = source.description;
    this.language = source.language;
    this.level = source.level;
    this.price = source.price; // ⭐ immutable value object: sharing the reference IS a copy
    this.priceSetAt = source.priceSetAt; // the pricing decision is content: it's copied too
    this.category = source.category; // another aggregate: referenced, never copied
    this.instructorId = source.instructorId; // the copy stays with the course's instructor
    this.instructorName = source.instructorName;
    this.status = CourseStatus.DRAFT;
    this.publishedAt = null;
    this.ratingAverage = BigDecimal.ZERO.setScale(2);
    this.ratingCount = 0;
    this.enrollmentCount = 0;
    this.createdAt = micros(now);
    this.updatedAt = this.createdAt;
    source.sections.forEach(section -> this.sections.add(new Section(this, section)));
    // the rollups are DERIVED: recompute from the copied curriculum rather than trusting the source
    this.lectureCount = sections.stream().mapToInt(s -> s.lectures().size()).sum();
    this.totalDuration =
        LectureDuration.total(
            sections.stream().flatMap(s -> s.lectures().stream()).map(Lecture::duration).toList());
  }

  private Course(
      String slug,
      String title,
      String description,
      CourseLevel level,
      String language,
      Money price,
      Category category,
      Instructor instructor,
      Instant now) {
    this.id = UUID.randomUUID();
    this.slug = requireSlug(slug);
    this.title = Lecture.requireTitle(title);
    this.description = Objects.requireNonNull(description, "description").strip();
    this.level = Objects.requireNonNull(level, "level");
    this.language = requireLanguage(language);
    this.price = requireCatalogCurrency(price);
    this.category = Objects.requireNonNull(category, "category");
    this.instructorId = instructor.id();
    this.instructorName = instructor.name();
    this.status = CourseStatus.DRAFT;
    this.ratingAverage = BigDecimal.ZERO.setScale(2);
    this.lectureCount = 0;
    this.totalDuration = LectureDuration.ZERO;
    this.createdAt = micros(now);
    this.updatedAt = this.createdAt;
  }

  /** ⭐ A named factory for the one legal way to start a course: as a DRAFT with no curriculum. */
  public static Course draft(
      String slug,
      String title,
      String description,
      CourseLevel level,
      String language,
      Money price,
      Category category,
      Instructor instructor,
      Instant now) {
    Objects.requireNonNull(instructor, "instructor");
    return new Course(slug, title, description, level, language, price, category, instructor, now);
  }

  // ------------------------------------------------------------------ curriculum

  public Section addSection(String title) {
    Section section = new Section(this, title, sections.size());
    sections.add(section);
    return section;
  }

  /** ⭐ Through the root, so the rollups move in the same call that adds the lecture. */
  public Lecture addLecture(
      Section section,
      String title,
      LectureKind kind,
      boolean preview,
      LectureDuration duration,
      UUID assetId) {
    if (section.course() != this) {
      throw new IllegalArgumentException("that section belongs to another course");
    }
    Lecture lecture = section.addLecture(title, kind, preview, duration, assetId);
    lectureCount++;
    totalDuration = totalDuration.plus(duration);
    return lecture;
  }

  // ------------------------------------------------------------------ duplication (Prototype)

  /**
   * A new DRAFT course with this one's content and curriculum (docs/lld/catalog.md §5). Callers
   * choose the slug (it must be unique; the service generates one).
   */
  public Course duplicateAsDraft(String newSlug, Instant now) {
    return new Course(this, newSlug, now);
  }

  private static String copyTitle(String title) {
    String suffix = " (copy)";
    return title.length() + suffix.length() <= 120
        ? title + suffix
        : title.substring(0, 120 - suffix.length()) + suffix;
  }

  // ------------------------------------------------------------------ lifecycle (State)

  /** The State object for the persisted status (docs/lld/catalog-authoring.md §3). */
  public CourseState state() {
    return CourseState.of(status);
  }

  /**
   * Moves the course through its lifecycle.
   *
   * <ol>
   *   <li>The STATE decides whether the action is legal from here ({@code ILLEGAL_TRANSITION}).
   *   <li>Submit and publish re-run the PUBLISH GATE against the course AS IT IS NOW — a course
   *       edited while waiting in review is checked again ({@code COURSE_NOT_READY}, 422).
   *   <li>{@code publishedAt} is stamped on the first publish and never moves.
   *   <li>{@code touch} bumps the version, so every open editor tab becomes stale.
   * </ol>
   */
  public void transition(CourseAction action, Instant now) {
    CourseStatus next = state().on(action);
    if (action.isGated()) {
      List<PublishCheck> problems = PublishGate.problems(this);
      if (!problems.isEmpty()) {
        throw new RuleViolationException(
            "COURSE_NOT_READY", "The course isn't ready yet.", Map.of("problems", problems));
      }
    }
    status = next;
    if (next == CourseStatus.PUBLISHED && publishedAt == null) {
      publishedAt = micros(now);
    }
    touch(now);
  }

  /** Content writes go through this: an archived course is read-only. */
  public void requireEditable() {
    if (!state().acceptsEdits()) {
      throw new ConflictException("COURSE_ARCHIVED", "An archived course can't be changed.");
    }
  }

  // ------------------------------------------------------------------ details

  /**
   * The wizard's "details" step. The slug is NOT regenerated from a new title: a changed URL is a
   * broken link (catalog.md §3).
   */
  public void changeDetails(
      String newTitle,
      String newSubtitle,
      String newDescription,
      CourseLevel newLevel,
      String newLanguage,
      Category newCategory,
      Instant now) {
    requireEditable();
    title = Lecture.requireTitle(newTitle);
    changeSubtitle(newSubtitle);
    description = Objects.requireNonNull(newDescription, "description").strip();
    level = Objects.requireNonNull(newLevel, "level");
    language = requireLanguage(newLanguage);
    category = Objects.requireNonNull(newCategory, "category");
    touch(now);
  }

  // ------------------------------------------------------------------ pricing

  /** Confirms the price (free or paid): the decision the publish gate waits for. */
  public void confirmPrice(Money newPrice, Instant now) {
    requireEditable();
    price = requireCatalogCurrency(newPrice);
    priceSetAt = micros(now);
    touch(now);
  }

  /**
   * ⭐ Marks the ROOT as changed. JPA bumps {@code @Version} only when the course row itself is
   * updated; a lecture rename updates only the lecture row. Every mutation through the root calls
   * this, so the course's version covers the whole aggregate (ADR-0010).
   */
  void touch(Instant now) {
    updatedAt = micros(now);
  }

  /** The rating summary is owned by engagement (Phase 11); catalog stores it to sort by it. */
  public void updateRatingSummary(BigDecimal average, int count) {
    if (count < 0 || average.signum() < 0 || average.compareTo(MAX_RATING) > 0) {
      throw new IllegalArgumentException("a rating average is 0–5 over a non-negative count");
    }
    if ((count == 0) != (average.signum() == 0)) {
      throw new IllegalArgumentException("an average needs ratings, and ratings need an average");
    }
    ratingAverage = average.setScale(2, java.math.RoundingMode.HALF_UP);
    ratingCount = count;
  }

  // ------------------------------------------------------------------ rules

  /** Owners see their own course in every state (LLD §5); admins are handled by the caller. */
  public boolean isOwnedBy(UUID userId) {
    return instructorId.equals(userId);
  }

  public boolean isPublished() {
    return status == CourseStatus.PUBLISHED;
  }

  private static String requireSlug(String slug) {
    if (slug == null || slug.length() > 140 || !SLUG.matcher(slug).matches()) {
      throw new IllegalArgumentException("a slug is lower-case words joined by '-': " + slug);
    }
    return slug;
  }

  private static Money requireCatalogCurrency(Money price) {
    Objects.requireNonNull(price, "price");
    if (!price.currency().equals(CATALOG_CURRENCY)) {
      throw new IllegalArgumentException(
          "catalog prices are in " + CATALOG_CURRENCY + ", not " + price.currency());
    }
    return price;
  }

  private static String requireLanguage(String language) {
    if (language == null || !LANGUAGE.matcher(language).matches()) {
      throw new IllegalArgumentException("a language is an ISO 639-1 code like 'en': " + language);
    }
    return language;
  }

  /**
   * ⭐ Postgres keeps MICROseconds; Java's clock has nanoseconds. Truncating here makes the value in
   * memory equal the value read back — which matters the moment it becomes a keyset cursor (5.5).
   */
  private static Instant micros(Instant now) {
    return Objects.requireNonNull(now, "now").truncatedTo(ChronoUnit.MICROS);
  }

  // ------------------------------------------------------------------ accessors

  public UUID id() {
    return id;
  }

  public String slug() {
    return slug;
  }

  public String title() {
    return title;
  }

  public Optional<String> subtitle() {
    return Optional.ofNullable(subtitle);
  }

  public void changeSubtitle(String subtitle) {
    String s = subtitle == null ? null : subtitle.strip();
    if (s != null && s.length() > 200) {
      throw new IllegalArgumentException("a subtitle has at most 200 characters");
    }
    this.subtitle = s == null || s.isEmpty() ? null : s;
  }

  public String description() {
    return description;
  }

  public String language() {
    return language;
  }

  public CourseLevel level() {
    return level;
  }

  public CourseStatus status() {
    return status;
  }

  public Optional<Instant> publishedAt() {
    return Optional.ofNullable(publishedAt);
  }

  public Optional<Instant> priceSetAt() {
    return Optional.ofNullable(priceSetAt);
  }

  public Money price() {
    return price;
  }

  public Instructor instructor() {
    return new Instructor(instructorId, instructorName);
  }

  public Category category() {
    return category;
  }

  public BigDecimal ratingAverage() {
    return ratingAverage;
  }

  public int ratingCount() {
    return ratingCount;
  }

  public int enrollmentCount() {
    return enrollmentCount;
  }

  public int lectureCount() {
    return lectureCount;
  }

  public LectureDuration totalDuration() {
    return totalDuration;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Long version() {
    return version;
  }

  /** Read-only: the curriculum changes only through this aggregate root. */
  public List<Section> sections() {
    return Collections.unmodifiableList(sections);
  }
}
