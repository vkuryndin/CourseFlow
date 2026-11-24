package org.courseflow.testing;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Parameter;
import jakarta.persistence.TypedQuery;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Typed query doubles for unit testing query composition, not JPQL execution. */
public final class QueryMocks {
    private QueryMocks() {
    }

    @SuppressWarnings("unchecked")
    public static <T> TypedQuery<T> query(EntityManager em, Class<T> type, List<T> rows, String... parameters) {
        TypedQuery<T> query = mock(TypedQuery.class, RETURNS_SELF);
        Set<Parameter<?>> declared = new HashSet<>();
        for (String name : parameters) {
            Parameter<?> parameter = mock(Parameter.class);
            when(parameter.getName()).thenReturn(name);
            declared.add(parameter);
        }
        when(query.getParameters()).thenReturn(declared);
        when(query.getResultList()).thenReturn(rows);
        when(em.createQuery(anyString(), eq(type))).thenReturn(query);
        return query;
    }

    public static TypedQuery<Long> count(EntityManager em, long total, String... parameters) {
        TypedQuery<Long> query = query(em, Long.class, List.of(), parameters);
        when(query.getSingleResult()).thenReturn(total);
        return query;
    }
}
