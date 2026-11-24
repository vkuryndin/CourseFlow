package org.courseflow.catalog.service;

import org.courseflow.common.api.Pagination;
import org.courseflow.common.api.SearchPatterns;
import jakarta.persistence.criteria.Predicate;
import org.courseflow.catalog.dto.CourseResponse;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CourseQueryService {
    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of(
                    "id",
                    "title",
                    "duration",
                    "startDate",
                    "status"
            );

    private final CourseRepository courseRepository;

    public CourseQueryService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Transactional(readOnly = true)
    public CourseResponse getById(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Course not found: " + id
                        )
                );

        return CourseResponse.from(course);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> findAll(
            int page,
            int size,
            String q,
            Long categoryId,
            Long teacherId,
            CourseStatus status,
            String sortBy,
            String direction
    ) {
        Pagination.validate(page, size);

        if (categoryId != null && categoryId < 1) {
            throw new BadRequestException(
                    "categoryId must be positive"
            );
        }

        if (teacherId != null && teacherId < 1) {
            throw new BadRequestException(
                    "teacherId must be positive"
            );
        }

        Pagination.requireSortField(sortBy, ALLOWED_SORT_FIELDS, "Unsupported sort field: " + sortBy);

        String sortDirection = Pagination.direction(direction);

        String search = q == null
                ? ""
                : q.trim().toLowerCase(Locale.ROOT);

        if (search.length() > 200) {
            throw new BadRequestException(
                    "Search query is too long"
            );
        }

        Specification<Course> specification =
                (root, query, builder) -> {

                    List<Predicate> predicates =
                            new ArrayList<>();

                    if (!search.isEmpty()) {
                        String pattern = SearchPatterns.containsLiteral(search);

                        Predicate titleMatches = builder.like(
                                builder.lower(root.get("title")),
                                pattern, '\\'
                        );

                        Predicate descriptionMatches = builder.like(
                                builder.lower(root.get("description")),
                                pattern, '\\'
                        );

                        predicates.add(
                                builder.or(
                                        titleMatches,
                                        descriptionMatches
                                )
                        );
                    }

                    if (categoryId != null) {
                        predicates.add(
                                builder.equal(
                                        root.get("category").get("id"),
                                        categoryId
                                )
                        );
                    }

                    if (teacherId != null) {
                        predicates.add(
                                builder.equal(
                                        root.get("teacher").get("id"),
                                        teacherId
                                )
                        );
                    }

                    if (status != null) {
                        predicates.add(
                                builder.equal(
                                        root.get("status"),
                                        status
                                )
                        );
                    }

                    return builder.and(
                            predicates.toArray(new Predicate[0])
                    );
                };

        Sort sort = Pagination.stableSort(sortBy, sortDirection);

        PageRequest pageable = PageRequest.of(page, size, sort);

        Page<CourseResponse> result = courseRepository
                .findAll(specification, pageable)
                .map(CourseResponse::from);

        return PageResponse.from(result);
    }

}
