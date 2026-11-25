package org.courseflow.identity.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.courseflow.common.api.PageResponse;
import org.courseflow.identity.dto.PublicTeacherResponse;
import org.courseflow.identity.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teachers")
public class PublicTeacherController {
  private final UserService userService;

  public PublicTeacherController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public PageResponse<PublicTeacherResponse> findAll(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "") @Size(max = 120) String q) {
    return userService.findPublicTeachers(page, size, q);
  }

  @GetMapping("/{id}")
  public PublicTeacherResponse get(@PathVariable Long id) {
    return userService.getPublicTeacher(id);
  }
}
