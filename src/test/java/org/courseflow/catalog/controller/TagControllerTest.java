package org.courseflow.catalog.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.courseflow.catalog.dto.TagRequest;
import org.courseflow.catalog.dto.TagResponse;
import org.courseflow.catalog.service.TagService;
import org.courseflow.common.api.PageResponse;
import org.junit.jupiter.api.Test;

/**
 * Direct controller contract tests; no Spring MVC runtime.
 */
class TagControllerTest {
    private final TagService tagService = mock(TagService.class);
    private final TagController controller = new TagController(tagService);

    @Test
    void findAllDelegatesAndReturnsContract() {
        int page = 1;
        int size = 15;
        String q = "search";
        String sortBy = "id";
        String direction = "desc";
        PageResponse<TagResponse> expected = new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
        when(tagService.findAll(page, size, q, sortBy, direction)).thenReturn(expected);

        var actual = controller.findAll(page, size, q, sortBy, direction);

        assertSame(expected, actual);
        verify(tagService).findAll(page, size, q, sortBy, direction);
    }

    @Test
    void getByIdDelegatesAndReturnsContract() {
        Long id = 101L;
        TagResponse expected = mock(TagResponse.class);
        when(tagService.getById(id)).thenReturn(expected);

        var actual = controller.getById(id);

        assertSame(expected, actual);
        verify(tagService).getById(id);
    }

    @Test
    void createDelegatesAndReturnsContract() {
        TagRequest request = mock(TagRequest.class);
        TagResponse expected = mock(TagResponse.class);
        when(expected.id()).thenReturn(999L);
        when(tagService.create(request)).thenReturn(expected);

        var actual = controller.create(request);

        assertEquals(201, actual.getStatusCode().value());
        assertSame(expected, actual.getBody());
        assertEquals("/api/tags/999", actual.getHeaders().getLocation().toString());
        verify(tagService).create(request);
    }

    @Test
    void updateDelegatesAndReturnsContract() {
        Long id = 101L;
        TagRequest request = mock(TagRequest.class);
        TagResponse expected = mock(TagResponse.class);
        when(tagService.update(id, request)).thenReturn(expected);

        var actual = controller.update(id, request);

        assertSame(expected, actual);
        verify(tagService).update(id, request);
    }

    @Test
    void deleteDelegatesAndReturnsContract() {
        Long id = 101L;

        var actual = controller.delete(id);

        assertEquals(204, actual.getStatusCode().value());
        assertNull(actual.getBody());
        verify(tagService).delete(id);
    }
}
