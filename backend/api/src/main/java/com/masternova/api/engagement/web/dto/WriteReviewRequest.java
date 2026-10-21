package com.masternova.api.engagement.web.dto;

import com.masternova.api.engagement.domain.Review;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create or rewrite the caller's review: a rating, and optionally words. */
public record WriteReviewRequest(
    @NotNull @Min(1) @Max(5) Integer rating, @Size(max = Review.MAX_BODY) String body) {}
