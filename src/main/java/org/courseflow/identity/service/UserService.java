
package org.courseflow.identity.service;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.dto.UserResponse;
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

    @Transactional(readOnly = true)
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

        Sort.Direction sortDirection;

        if ("asc".equalsIgnoreCase(direction)) {
            sortDirection = Sort.Direction.ASC;
        } else if ("desc".equalsIgnoreCase(direction)) {
            sortDirection = Sort.Direction.DESC;
        } else {
            throw new BadRequestException(
                    "Direction must be asc or desc"
            );
        }

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

        Page<UserResponse> result = userRepository
                .findAll(specification, pageable)
                .map(UserResponse::from);

        return PageResponse.from(result);
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

                // Treat % and _ as ordinary search characters.
                search = search
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");

                String pattern = "%" + search + "%";

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
