package org.courseflow.catalog.service;

import org.courseflow.common.api.Pagination;
import org.courseflow.catalog.dto.TagRequest;
import org.courseflow.catalog.dto.TagResponse;
import org.courseflow.catalog.entity.Tag;
import org.courseflow.catalog.repository.TagRepository;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
public class TagService {

    private static final Set<String> SORT_FIELDS =
            Set.of("id", "name");

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TagResponse> findAll(
            int page,
            int size,
            String q,
            String sortBy,
            String direction
    ) {
        Pagination.validate(page, size);

        if (!SORT_FIELDS.contains(sortBy)) {
            throw new BadRequestException(
                    "Unsupported sort field: " + sortBy
            );
        }

        String normalizedDirection = Pagination.direction(direction);

        String search = q == null ? "" : q.trim();

        if (search.length() > 100) {
            throw new BadRequestException(
                    "Search query is too long"
            );
        }

        Sort.Direction sortDirection =
                normalizedDirection.equals("desc")
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC;

        Sort sort = Sort.by(sortDirection, sortBy);

        if (!sortBy.equals("id")) {
            sort = sort.and(Sort.by("id"));
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                sort
        );

        Page<TagResponse> result = tagRepository
                .findByNameContainingIgnoreCase(search, pageable)
                .map(TagResponse::from);

        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public TagResponse getById(Long id) {
        return TagResponse.from(findTag(id));
    }

    @Transactional
    public TagResponse create(TagRequest request) {
        String name = request.name().trim();

        checkName(name, null);

        Tag tag = new Tag(name);

        try {
            return TagResponse.from(
                    tagRepository.saveAndFlush(tag)
            );
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessConflictException(
                    "Tag name already exists"
            );
        }
    }

    @Transactional
    public TagResponse update(
            Long id,
            TagRequest request
    ) {
        Tag tag = findTag(id);

        String name = request.name().trim();

        checkName(name, id);

        tag.rename(name);

        try {
            tagRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessConflictException(
                    "Tag name already exists"
            );
        }

        return TagResponse.from(tag);
    }

    @Transactional
    public void delete(Long id) {
        Tag tag = findTag(id);

        if (tagRepository.countCoursesByTagId(id) > 0) {
            throw new BusinessConflictException(
                    "Tag is used by courses"
            );
        }

        try {
            tagRepository.delete(tag);
            tagRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessConflictException(
                    "Tag cannot be deleted because "
                            + "related records exist"
            );
        }
    }

    private Tag findTag(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Tag not found: " + id
                        )
                );
    }

    private void checkName(
            String name,
            Long currentId
    ) {
        if (name.isBlank()) {
            throw new BadRequestException(
                    "Tag name cannot be blank"
            );
        }

        boolean exists = currentId == null
                ? tagRepository.existsByNameIgnoreCase(name)
                : tagRepository.existsByNameIgnoreCaseAndIdNot(
                name,
                currentId
        );

        if (exists) {
            throw new BusinessConflictException(
                    "Tag name already exists"
            );
        }
    }
}
