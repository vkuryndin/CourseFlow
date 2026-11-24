package org.courseflow.common.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.courseflow.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class PaginationTest {

    @Test
    void validBoundaries() {
        assertDoesNotThrow(() -> Pagination.validate(0, 1));
        assertDoesNotThrow(() -> Pagination.validate(1, 100));
        assertDoesNotThrow(() -> Pagination.validate(Integer.MAX_VALUE, 1));
    }

    @Test
    void rejectsNegativePage() {
        assertThrows(BadRequestException.class, () -> Pagination.validate(-1, 20));
    }

    @Test
    void rejectsInvalidSize() {
        for (int size : new int[]{-1, 0, 101}) {
            assertThrows(BadRequestException.class, () -> Pagination.validate(0, size));
        }
    }

    @Test
    void rejectsOffsetOverflow() {
        assertThrows(BadRequestException.class, () -> Pagination.validate(Integer.MAX_VALUE, 2));
    }

    @Test
    void normalizesDirection() {
        assertEquals("asc", Pagination.direction("ASC"));
        assertEquals("desc", Pagination.direction("DeSc"));
    }

    @Test
    void rejectsInvalidDirection() {
        for (String dir : Arrays.asList(null, "", "drop", " asc ")) {
            assertThrows(BadRequestException.class, () -> Pagination.direction(dir));
        }
    }

    @Test
    void enforcesAllowlist() {
        assertDoesNotThrow(() -> Pagination.requireSortField("name", Set.of("name"), "bad"));
        assertThrows(BadRequestException.class, () -> Pagination.requireSortField(null, Set.of("name"), "bad"));
        assertThrows(BadRequestException.class, () -> Pagination.requireSortField("secret", Set.of("name"), "bad"));
    }

    @Test
    void stableSortUsesIdTieBreaker() {
        assertEquals(List.of(Sort.Order.desc("name"), Sort.Order.asc("id")),
                Pagination.stableSort("name", "desc").toList());
        assertEquals(List.of(Sort.Order.desc("id")), Pagination.stableSort("id", "desc").toList());
    }

    @Test
    void jpqlOrderingUsesTieBreakerOnlyWhenNeeded() {
        assertEquals(" ORDER BY u.name desc, u.id ASC", Pagination.orderBy("u.name", "DESC", "u.id"));
        assertEquals(" ORDER BY u.id asc", Pagination.orderBy("u.id", "asc", "u.id"));
    }

    @Test
    void literalSearchEscapesWildcards() {
        assertEquals("%a\\\\b\\%c\\_d%", SearchPatterns.containsLiteral("a\\b%c_d"));
        assertEquals("%%", SearchPatterns.containsLiteral(""));
    }

    @Test
    void pageResponsePreservesMetadata() {
        var response = PageResponse.from(new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5));

        assertEquals(List.of("a", "b"), response.content());
        assertEquals(1, response.page());
        assertEquals(2, response.size());
        assertEquals(5, response.totalElements());
        assertEquals(3, response.totalPages());
        assertFalse(response.first());
        assertFalse(response.last());
    }
}
