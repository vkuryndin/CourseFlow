package org.courseflow.catalog.service;

import org.courseflow.common.api.Pagination;
import org.courseflow.common.exception.EditVersion;
import org.courseflow.catalog.dto.CategoryCreateRequest;
import org.courseflow.catalog.dto.CategoryResponse;
import org.courseflow.catalog.entity.Category;
import org.courseflow.catalog.repository.CategoryRepository;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.api.PageResponse;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    private final CourseRepository courseRepository;

    public CategoryService(CategoryRepository categoryRepository,
                           CourseRepository courseRepository) {
        this.categoryRepository = categoryRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryCreateRequest request) {
        Category category = lockCategory(id);
        EditVersion.require(request.version(), category.getVersion());
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new BusinessConflictException("Category already exists: " + name);
        }
        category.update(name, request.description().trim());
        categoryRepository.flush();
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = lockCategory(id);
        if (courseRepository.existsByCategory_Id(id)) {
            throw new BusinessConflictException("Category is used by courses");
        }
        categoryRepository.delete(category);
        categoryRepository.flush();
    }

    private Category lockCategory(Long id) {
        return categoryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        String name = request.name().trim();

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessConflictException(
                    "Category already exists: " + name
            );
        }

        Category category = new Category(
                name,
                request.description().trim()
        );

        Category saved = categoryRepository.saveAndFlush(category);

        return CategoryResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Category not found: " + id
                        )
                );

        return CategoryResponse.from(category);
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> findAll(
            int page,
            int size,
            String search,
            String sortBy,
            String direction
    ) {
        if (!sortBy.equals("id") && !sortBy.equals("name")) {
            throw new BadRequestException(
                    "Sorting is supported only by id or name"
            );
        }

        Pagination.validate(page, size);
        Sort.Direction sortDirection = Sort.Direction.fromString(Pagination.direction(direction));

        Sort sort = Sort.by(sortDirection, sortBy);
        if (!sortBy.equals("id")) {
            sort = sort.and(Sort.by("id"));
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                sort
        );

        Page<Category> categories;

        if (search == null || search.isBlank()) {
            categories = categoryRepository.findAll(pageable);
        } else {
            categories =
                    categoryRepository.findByNameContainingIgnoreCase(
                            search.trim(),
                            pageable
                    );
        }

        Page<CategoryResponse> responses =
                categories.map(CategoryResponse::from);

        return PageResponse.from(responses);
    }
}
