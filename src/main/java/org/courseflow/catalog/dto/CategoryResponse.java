package org.courseflow.catalog.dto;

import org.courseflow.catalog.entity.Category;

public record CategoryResponse(Long id, long version, String name, String description) {

  public static CategoryResponse from(Category category) {
    return new CategoryResponse(
        category.getId(), category.getVersion(), category.getName(), category.getDescription());
  }
}
