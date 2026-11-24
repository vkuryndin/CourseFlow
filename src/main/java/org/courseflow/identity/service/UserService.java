package org.courseflow.identity.service;

import org.courseflow.common.api.Pagination;
import org.courseflow.common.api.SearchPatterns;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.dto.UserResponse;
import org.courseflow.identity.dto.PublicTeacherResponse;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.identity.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UserService {

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "displayName", "email");

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + id
                        )
                );

        return UserResponse.from(user);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<UserResponse> findAll(
            int page,
            int size,
            String q,
            UserStatus status,
            UserRole role,
            String sortBy,
            String direction
    ) {
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new BadRequestException(
                    "Sorting is supported only by id, "
                            + "displayName or email"
            );
        }

        Pagination.validate(page, size);
        Sort.Direction sortDirection = Sort.Direction.fromString(Pagination.direction(direction));

        Sort sort = Sort.by(sortDirection, sortBy);

        if (!"id".equals(sortBy)) {
            sort = sort.and(Sort.by("id"));
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                sort
        );

        Specification<User> specification =
                buildSpecification(q, status, role);

        Page<User> users = userRepository.findAll(specification, pageable);
        if (users.hasContent()) {
            // Fetch all roles for the current page in one query, in the same persistence context.
            // Do not fetch-join the collection in the paginated query.
            userRepository.findWithRolesByIds(users.getContent().stream().map(User::getId).toList());
        }
        return PageResponse.from(users.map(UserResponse::from));
    }

    @Transactional(readOnly = true)
    public PublicTeacherResponse getPublicTeacher(Long id) {
        User teacher = userRepository.findById(id)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && u.getRoles().contains(UserRole.TEACHER))
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + id));
        return new PublicTeacherResponse(teacher.getId(), teacher.getDisplayName());
    }

    private Specification<User> buildSpecification(
            String q,
            UserStatus status,
            UserRole role
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (q != null && !q.isBlank()) {
                String search = q.trim()
                        .toLowerCase(Locale.ROOT);

                String pattern = SearchPatterns.containsLiteral(search);

                Predicate byName = cb.like(
                        cb.lower(root.get("displayName")),
                        pattern,
                        '\\'
                );

                Predicate byEmail = cb.like(
                        cb.lower(root.get("email")),
                        pattern,
                        '\\'
                );

                predicates.add(cb.or(byName, byEmail));
            }

            if (status != null) {
                predicates.add(
                        cb.equal(root.get("status"), status)
                );
            }

            if (role != null) {
                query.distinct(true);

                predicates.add(
                        cb.equal(
                                root.join("roles", JoinType.INNER),
                                role
                        )
                );
            }

            return cb.and(
                    predicates.toArray(Predicate[]::new)
            );
        };
    }
}
