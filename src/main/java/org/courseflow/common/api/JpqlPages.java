package org.courseflow.common.api;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** Execute a matching data/count query pair inside the caller's transaction. */
public final class JpqlPages {
    private JpqlPages() {}

    public record Result<T>(List<T> rows, long total, int page, int size) {
        public PageResponse<T> response() {
            return PageResponse.from(new PageImpl<>(rows, PageRequest.of(page, size), total));
        }
    }

    public static <T> Result<T> findPage(EntityManager em, Class<T> type,
                                         String select, String count, String from,
                                         String order, Map<String, Object> parameters,
                                         int page, int size) {
        Pagination.validate(page, size);
        TypedQuery<T> data = em.createQuery(select + " " + from + order, type);
        TypedQuery<Long> total = em.createQuery("SELECT " + count + " " + from, Long.class);
        bind(data, parameters);
        bind(total, parameters);
        List<T> rows = data.setFirstResult(page * size).setMaxResults(size).getResultList();
        return new Result<>(rows, total.getSingleResult(), page, size);
    }

    private static void bind(TypedQuery<?> query, Map<String, Object> parameters) {
        query.getParameters().forEach(parameter -> {
            String name = parameter.getName();
            if (!parameters.containsKey(name)) {
                throw new IllegalArgumentException("Missing JPQL parameter: " + name);
            }
            query.setParameter(name, parameters.get(name));
        });
    }
}
