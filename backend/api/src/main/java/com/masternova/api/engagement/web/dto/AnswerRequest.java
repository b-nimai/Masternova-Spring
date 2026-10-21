package com.masternova.api.engagement.web.dto;

import com.masternova.api.engagement.domain.Answer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * @param parentAnswerId the answer replied to; omitted to answer the question itself
 */
public record AnswerRequest(
    UUID parentAnswerId, @NotBlank @Size(max = Answer.MAX_BODY) String body) {}
