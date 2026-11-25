package org.courseflow.content.controller;

import jakarta.validation.Valid;
import java.net.URI;
import org.courseflow.common.api.PageResponse;
import org.courseflow.content.dto.ChangeModulePositionRequest;
import org.courseflow.content.dto.CreateModuleRequest;
import org.courseflow.content.dto.ModuleResponse;
import org.courseflow.content.dto.UpdateModuleRequest;
import org.courseflow.content.service.ModuleService;
import org.courseflow.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses/{courseId}/modules")
public class ModuleController {

  private final ModuleService moduleService;

  public ModuleController(ModuleService moduleService) {
    this.moduleService = moduleService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<ModuleResponse> create(
      @PathVariable Long courseId,
      @Valid @RequestBody CreateModuleRequest request,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    ModuleResponse response =
        moduleService.create(
            courseId, request, CurrentUser.id(jwt), CurrentUser.isAdmin(authentication));

    URI location = URI.create("/api/courses/" + courseId + "/modules/" + response.id());

    return ResponseEntity.created(location).body(response);
  }

  @GetMapping
  public PageResponse<ModuleResponse> findByCourse(
      @PathVariable Long courseId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return moduleService.findByCourse(courseId, page, size);
  }

  @GetMapping("/{moduleId}")
  public ModuleResponse getById(@PathVariable Long courseId, @PathVariable Long moduleId) {
    return moduleService.getById(courseId, moduleId);
  }

  @PutMapping("/{moduleId}")
  public ModuleResponse update(
      @PathVariable Long courseId,
      @PathVariable Long moduleId,
      @Valid @RequestBody UpdateModuleRequest request,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    return moduleService.update(
        courseId, moduleId, request, CurrentUser.id(jwt), CurrentUser.isAdmin(authentication));
  }

  @PatchMapping("/{moduleId}/position")
  public ModuleResponse changePosition(
      @PathVariable Long courseId,
      @PathVariable Long moduleId,
      @Valid @RequestBody ChangeModulePositionRequest request,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    return moduleService.changePosition(
        courseId, moduleId, request, CurrentUser.id(jwt), CurrentUser.isAdmin(authentication));
  }

  @DeleteMapping("/{moduleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public ResponseEntity<Void> delete(
      @PathVariable Long courseId,
      @PathVariable Long moduleId,
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication) {
    moduleService.delete(
        courseId, moduleId, CurrentUser.id(jwt), CurrentUser.isAdmin(authentication));

    return ResponseEntity.noContent().build();
  }
}
