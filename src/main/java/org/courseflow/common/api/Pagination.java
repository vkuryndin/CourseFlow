package org.courseflow.common.api;

import org.springframework.data.domain.Sort;
import java.util.Locale;
import org.courseflow.common.exception.BadRequestException;

public final class Pagination {
    private Pagination() {}

    public static void validate(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size) {
            throw new BadRequestException("Invalid pagination parameters");
        }
    }

    public static String direction(String direction) {
        if (direction == null) {
            throw new BadRequestException("Direction must be asc or desc");
        }
        String normalized = direction.toLowerCase(Locale.ROOT);
        if (!normalized.equals("asc") && !normalized.equals("desc")) {
            throw new BadRequestException("Direction must be asc or desc");
        }
        return normalized;
    }
    public static Sort stableSort(String sortBy, String direction) {
        Sort sort = Sort.by(Sort.Direction.fromString(direction(direction)), sortBy);
        return "id".equals(sortBy) ? sort : sort.and(Sort.by("id"));
    }
}
