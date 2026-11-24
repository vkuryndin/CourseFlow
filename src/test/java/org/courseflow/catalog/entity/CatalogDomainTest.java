package org.courseflow.catalog.entity;

import static org.courseflow.testing.Fixtures.category;
import static org.courseflow.testing.Fixtures.course;
import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.module;
import static org.courseflow.testing.Fixtures.teacher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.courseflow.content.entity.Module;
import org.junit.jupiter.api.Test;

class CatalogDomainTest {

    @Test
    void courseDefaultsAndUpdate() {
        Course course = course();

        assertEquals(CourseStatus.ACTIVE, course.getStatus());
        Category category = new Category("SQL", "Databases");
        course.updateDetails("New", "Description", category, 45, LocalDate.of(2026, 2, 1));
        assertEquals("New", course.getTitle());
        assertEquals("Description", course.getDescription());
        assertSame(category, course.getCategory());
        assertEquals(45, course.getDuration());
        assertEquals(LocalDate.of(2026, 2, 1), course.getStartDate());
    }

    @Test
    void archiveAndTeacherReassignment() {
        Course course = course();
        var replacement = teacher();
        course.reassignTeacher(replacement);

        assertSame(replacement, course.getTeacher());
        course.archive();
        assertEquals(CourseStatus.ARCHIVED, course.getStatus());
    }

    @Test
    void moduleMaintainsBothSides() {
        Course course = course();
        Module module = course.addModule("SQL", "Queries", 2);

        assertSame(course, module.getCourse());
        assertEquals(List.of(module), course.getModules());
        assertThrows(UnsupportedOperationException.class, () -> course.getModules().clear());
    }

    @Test
    void tagAssociationAndRemoval() {
        Course course = course();
        Tag tag = id(new Tag("JPA"), 1);
        course.addTag(tag);
        course.addTag(tag);

        assertEquals(Set.of(tag), course.getTags());
        course.removeTag(id(new Tag("JPA"), 1));
        assertTrue(course.getTags().isEmpty());
    }

    @Test
    void categoryAndTagRename() {
        Category category = category();
        category.update("SQL", "Database");

        assertEquals("SQL", category.getName());
        assertEquals("Database", category.getDescription());
        Tag tag = new Tag("ORM");
        tag.rename("JPA");
        assertEquals("JPA", tag.getName());
    }
}
