package org.courseflow.integration.orm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.courseflow.catalog.entity.Category;
import org.courseflow.catalog.entity.Course;
import org.courseflow.content.entity.Module;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.integration.support.AbstractIntegrationTest;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Demonstrates lazy loading and two ways to avoid LazyInitializationException. */
class LazyInitializationIT extends AbstractIntegrationTest {

  @Autowired private EntityManagerFactory emf;

  private long courseId;
  private List<Long> expectedModuleIds;

  @BeforeEach
  void createCourseWithModules() {
    // Commit real fixtures in a separate persistence context.
    tx(
        em -> {
          String suffix = UUID.randomUUID().toString();
          var teacher =
              new User(
                  "Lazy loading teacher",
                  "lazy-" + suffix + "@courseflow.local",
                  Set.of(UserRole.TEACHER),
                  Instant.now());
          var category = new Category("Lazy loading " + suffix, "ORM demonstration");
          em.persist(teacher);
          em.persist(category);

          var course =
              new Course(
                  "Lazy loading " + suffix,
                  "Hibernate lazy loading demonstration",
                  category,
                  teacher,
                  56,
                  LocalDate.now(ZoneOffset.UTC).plusDays(1));
          var first = course.addModule("First module", "Module content", 1);
          var second = course.addModule("Second module", "Module content", 2);
          em.persist(course);
          em.flush();

          courseId = course.getId();
          expectedModuleIds = List.of(first.getId(), second.getId());
          return null;
        });
  }

  @Test
  void shouldThrowLazyInitializationException() {
    Course detachedCourse =
        tx(
            em -> {
              Course course = em.find(Course.class, courseId);
              assertNotNull(course);
              assertFalse(emf.getPersistenceUnitUtil().isLoaded(course, "modules"));
              return course;
            });

    // The EntityManager is closed by tx(); the collection was never initialized.
    assertFalse(emf.getPersistenceUnitUtil().isLoaded(detachedCourse, "modules"));
    assertThrows(LazyInitializationException.class, detachedCourse::getModules);
  }

  @Test
  void shouldLoadModulesWithinTransaction() {
    Course detachedCourse =
        tx(
            em -> {
              Course course = em.find(Course.class, courseId);
              assertNotNull(course);
              assertTrue(em.getTransaction().isActive());
              assertFalse(emf.getPersistenceUnitUtil().isLoaded(course, "modules"));

              // Accessing the lazy collection while the persistence context is open works.
              assertExpectedModules(course);
              assertTrue(emf.getPersistenceUnitUtil().isLoaded(course, "modules"));
              return course;
            });

    // An initialized collection remains accessible after detachment.
    assertExpectedModules(detachedCourse);
  }

  @Test
  void shouldLoadModulesUsingFetchJoin() {
    Course detachedCourse =
        tx(
            em -> {
              Course course =
                  em.createQuery(
                          """
                            select distinct c from Course c
                            join fetch c.modules
                            where c.id = :id
                            """,
                          Course.class)
                      .setParameter("id", courseId)
                      .getSingleResult();

              // Verify eager initialization by this query before calling the getter.
              assertTrue(emf.getPersistenceUnitUtil().isLoaded(course, "modules"));
              return course;
            });

    // JOIN FETCH loads the required association before the EntityManager closes.
    assertTrue(emf.getPersistenceUnitUtil().isLoaded(detachedCourse, "modules"));
    assertExpectedModules(detachedCourse);
  }

  private void assertExpectedModules(Course course) {
    assertEquals(expectedModuleIds, course.getModules().stream().map(Module::getId).toList());
  }

  private <T> T tx(Function<EntityManager, T> work) {
    EntityManager em = emf.createEntityManager();
    try {
      em.getTransaction().begin();
      T result = work.apply(em);
      em.getTransaction().commit();
      return result;
    } finally {
      try {
        if (em.getTransaction().isActive()) {
          em.getTransaction().rollback();
        }
      } finally {
        em.close();
      }
    }
  }
}
