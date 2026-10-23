package com.masternova.api.identity.web.dto;

import com.masternova.api.identity.domain.InstructorApplication;
import jakarta.validation.constraints.Size;

/**
 * @param pitch what the applicant wants to teach, for the admin who decides. Optional.
 */
public record ApplyAsInstructorRequest(@Size(max = InstructorApplication.PITCH_MAX) String pitch) {}
