package org.courseflow.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Parameter;
import jakarta.persistence.TypedQuery;
import org.courseflow.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class JpqlPagesTest {

    @SuppressWarnings("unchecked")
    @Test
    void bindsParametersAndAppliesPaginationToDataOnly() {
        EntityManager em = mock(EntityManager.class);
        TypedQuery<String> rows = mock(TypedQuery.class);
        TypedQuery<Long> count = mock(TypedQuery.class);
        Parameter<Long> parameter = mock(Parameter.class);
        when(parameter.getName()).thenReturn("id");
        when(em.createQuery("SELECT x FROM X x WHERE x.id = :id ORDER BY x.id", String.class)).thenReturn(rows);
        when(em.createQuery("SELECT COUNT(x) FROM X x WHERE x.id = :id", Long.class)).thenReturn(count);
        when(rows.getParameters()).thenReturn(Set.of(parameter));
        when(count.getParameters()).thenReturn(Set.of(parameter));
        when(rows.setFirstResult(20)).thenReturn(rows);
        when(rows.setMaxResults(10)).thenReturn(rows);
        when(rows.getResultList()).thenReturn(List.of("row"));
        when(count.getSingleResult()).thenReturn(21L);
        var r = JpqlPages.findPage(em, String.class, "SELECT x", "COUNT(x)", "FROM X x WHERE x.id = :id",
                " ORDER BY x.id", Map.of("id", 1L), 2, 10);

        assertEquals(List.of("row"), r.rows());
        assertEquals(21, r.total());
        assertEquals(3, r.response().totalPages());
        verify(rows).setParameter("id", 1L);
        verify(count).setParameter("id", 1L);
        verify(count, never()).setFirstResult(anyInt());
    }

    @Test
    void invalidPaginationDoesNotExecuteQueries() {
        EntityManager em = mock(EntityManager.class);

        assertThrows(BadRequestException.class,
                () -> JpqlPages.findPage(em, String.class, "SELECT x", "COUNT(x)", "FROM X x", "", Map.of(), -1, 10));
        verifyNoInteractions(em);
    }
}
