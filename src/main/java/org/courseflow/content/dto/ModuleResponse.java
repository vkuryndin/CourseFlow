package org.courseflow.content.dto;

import org.courseflow.content.entity.Module;

public record ModuleResponse(
    Long id, long version, Long courseId, String title, String description, Integer position) {

  public static ModuleResponse from(Module module) {
    return new ModuleResponse(
        module.getId(),
        module.getVersion(),
        module.getCourse().getId(),
        module.getTitle(),
        module.getDescription(),
        module.getPosition());
  }
}
