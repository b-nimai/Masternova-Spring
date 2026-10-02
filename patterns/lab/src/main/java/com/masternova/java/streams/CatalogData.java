package com.masternova.java.streams;

import com.masternova.java.valueobject.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** A small, realistic dataset for the stream queries and their tests. */
public final class CatalogData {

  private CatalogData() {}

  public static List<Course> courses() {
    return List.of(
        course("c1", "Spring Boot Fundamentals", "Backend", "Asha", 1_499, 4.7, 1_200, 9_500, true,
            Set.of("java", "spring"), "2026-01-10"),
        course("c2", "Java Streams Deep Dive", "Backend", "Ravi", 999, 4.9, 640, 4_100, true,
            Set.of("java", "functional"), "2026-02-14"),
        course("c3", "Angular Signals", "Frontend", "Meera", 1_299, 4.5, 820, 6_200, true,
            Set.of("angular", "typescript"), "2026-03-02"),
        course("c4", "Kubernetes for Developers", "DevOps", "Asha", 1_999, 4.8, 410, 3_300, true,
            Set.of("kubernetes", "docker"), "2026-04-20"),
        course("c5", "Docker in Practice", "DevOps", "Kabir", 0, 4.2, 2_300, 15_000, true,
            Set.of("docker"), "2025-11-05"),
        course("c6", "RxJS Patterns", "Frontend", "Meera", 899, 4.1, 150, 900, true,
            Set.of("angular", "rxjs", "typescript"), "2026-05-18"),
        course("c7", "System Design Basics", "Backend", "Ravi", 2_499, 4.6, 980, 7_400, true,
            Set.of("architecture"), "2026-06-01"),
        course("c8", "Terraform on AWS (draft)", "DevOps", "Kabir", 1_799, 0.0, 0, 0, false,
            Set.of("terraform", "aws"), null),
        course("c9", "CSS Grid in a Day", "Frontend", "Zoya", 0, 3.9, 75, 2_000, true,
            Set.of("css"), "2026-07-07"));
  }

  public static List<Sale> sales() {
    return List.of(
        sale("c1", 1_499, "2026-08-03"),
        sale("c1", 1_499, "2026-08-19"),
        sale("c2", 999, "2026-08-21"),
        sale("c3", 1_299, "2026-09-02"),
        sale("c4", 1_999, "2026-09-05"),
        sale("c1", 1_499, "2026-09-11"),
        sale("c7", 2_499, "2026-09-12"),
        sale("c6", 899, "2026-09-30"));
  }

  private static Course course(String id, String title, String category, String instructor,
      long rupees, double rating, int ratingCount, int enrollments, boolean published,
      Set<String> tags, String publishedOn) {
    return new Course(id, title, category, instructor, Money.of(rupees * 100, "INR"), rating,
        ratingCount, enrollments, published, tags,
        publishedOn == null ? null : LocalDate.parse(publishedOn));
  }

  private static Sale sale(String courseId, long rupees, String date) {
    return new Sale(courseId, Money.of(rupees * 100, "INR"), LocalDate.parse(date));
  }
}
