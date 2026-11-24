package org.courseflow.catalog.service;

import static org.courseflow.testing.Fixtures.category;
import static org.courseflow.testing.Fixtures.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.courseflow.catalog.dto.CategoryCreateRequest;
import org.courseflow.catalog.entity.Category;
import org.courseflow.catalog.repository.CategoryRepository;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class CategoryServiceTest {
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final CategoryService service = new CategoryService(categories, courses);

    @Test
    void createTrimsBothFields() {
        when(categories.saveAndFlush(any(Category.class))).thenAnswer((i) -> id(i.getArgument(0), 1));
        var r = service.create(new CategoryCreateRequest(null, " Java ", " Programming "));

        assertEquals("Java", r.name());
        assertEquals("Programming", r.description());
    }

    @Test
    void duplicateRejected() {
        when(categories.existsByNameIgnoreCase("Java")).thenReturn(true);

        assertThrows(BusinessConflictException.class,
                () -> service.create(new CategoryCreateRequest(null, "Java", "Programming")));
        verify(categories, never()).saveAndFlush(any());
    }

    @Test
    void updateRequiresCurrentVersion() {
        var c = category();
        when(categories.findByIdForUpdate(30L)).thenReturn(Optional.of(c));

        assertThrows(OptimisticLockingFailureException.class,
                () -> service.update(30L, new CategoryCreateRequest(1L, "SQL", "DB")));
        assertEquals("Java", c.getName());
        assertEquals("SQL", service.update(30L, new CategoryCreateRequest(0L, " SQL ", " DB ")).name());
        verify(categories).flush();
    }

    @Test
    void updateRejectsDuplicateName() {
        var c = category();
        when(categories.findByIdForUpdate(30L)).thenReturn(Optional.of(c));
        when(categories.existsByNameIgnoreCaseAndIdNot("SQL", 30L)).thenReturn(true);

        assertThrows(BusinessConflictException.class,
                () -> service.update(30L, new CategoryCreateRequest(0L, "SQL", "DB")));
        assertEquals("Java", c.getName());
    }

    @Test
    void categoryWithCoursesCannotBeDeleted() {
        when(categories.findByIdForUpdate(30L)).thenReturn(Optional.of(category()));
        when(courses.existsByCategory_Id(30L)).thenReturn(true);

        assertThrows(BusinessConflictException.class, () -> service.delete(30L));
        verify(categories, never()).delete(any(Category.class));
    }

    @Test
    void unusedCategoryDeleted() {
        var c = category();
        when(categories.findByIdForUpdate(30L)).thenReturn(Optional.of(c));
        service.delete(30L);

        verify(categories).delete(c);
        verify(categories).flush();
    }

    @Test
    void missingCategoryRejected() {
        assertThrows(ResourceNotFoundException.class, () -> service.getById(30L));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(30L));
    }
}
