package com.masternova.api.catalog.web.dto;

import com.masternova.api.catalog.domain.CurriculumCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * {@code {"expectedVersion": 7, "command": {"kind": "MOVE_LECTURE", "lectureId": "…", …}}} — API
 * conventions §6: the edit is a discriminated union, read by Jackson into the sealed command type.
 */
public record CurriculumCommandRequest(
    @NotNull @PositiveOrZero Long expectedVersion, @NotNull CurriculumCommand command) {}
