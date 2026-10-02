package com.masternova.api.learning.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data generates the implementation (a proxy — note 09) from this interface alone. */
interface LearningCourseRepository extends JpaRepository<LearningCourse, Long> {

  /** ⭐ Fix 1 for N+1: JOIN FETCH loads courses AND their sections in ONE query. */
  @Query("select distinct c from LearningCourse c left join fetch c.sections order by c.id")
  List<LearningCourse> findAllWithSections();

  /** ⭐ Fix 2 for N+1: an entity graph says "also fetch sections" without writing the join. */
  @EntityGraph(attributePaths = "sections")
  @Query("select c from LearningCourse c order by c.id")
  List<LearningCourse> findAllWithSectionsGraph();

  /** A derived query: Spring Data writes the JPQL from the method name. */
  List<LearningCourse> findByTitleContainingIgnoreCaseOrderByTitle(String fragment);
}
