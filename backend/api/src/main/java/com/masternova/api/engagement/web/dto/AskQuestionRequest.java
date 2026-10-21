package com.masternova.api.engagement.web.dto;

import com.masternova.api.engagement.domain.Question;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskQuestionRequest(
    @NotBlank @Size(max = Question.MAX_TITLE) String title,
    @Size(max = Question.MAX_BODY) String body) {}
