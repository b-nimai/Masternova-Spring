package com.masternova.api.catalog.web;

import com.masternova.api.catalog.application.CurriculumService;
import com.masternova.api.catalog.web.dto.CurriculumCommandRequest;
import com.masternova.api.catalog.web.dto.CurriculumResponse;
import com.masternova.api.catalog.web.dto.VersionRequest;
import com.masternova.api.identity.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ⭐ ONE route for every curriculum edit (the command's {@code kind} says which), plus undo/redo.
 */
@RestController
@RequestMapping("/api/v1/instructor/courses/{id}/curriculum")
class CurriculumController {

  private final CurriculumService curriculum;

  CurriculumController(CurriculumService curriculum) {
    this.curriculum = curriculum;
  }

  @GetMapping
  CurriculumResponse get(@PathVariable UUID id, CurrentUser me) {
    return CurriculumResponse.from(curriculum.get(id, Viewers.of(me)));
  }

  @PostMapping
  CurriculumResponse apply(
      @PathVariable UUID id, @Valid @RequestBody CurriculumCommandRequest request, CurrentUser me) {
    return CurriculumResponse.from(
        curriculum.apply(
            id, request.expectedVersion(), request.command(), Viewers.of(me), me.id()));
  }

  @PostMapping("/undo")
  CurriculumResponse undo(
      @PathVariable UUID id, @Valid @RequestBody VersionRequest request, CurrentUser me) {
    return CurriculumResponse.from(curriculum.undo(id, request.expectedVersion(), Viewers.of(me)));
  }

  @PostMapping("/redo")
  CurriculumResponse redo(
      @PathVariable UUID id, @Valid @RequestBody VersionRequest request, CurrentUser me) {
    return CurriculumResponse.from(curriculum.redo(id, request.expectedVersion(), Viewers.of(me)));
  }
}
