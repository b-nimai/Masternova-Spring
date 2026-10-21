/**
 * Engagement module — learners' reviews of the courses they take (with the exact rating tally
 * behind every catalog card's stars, ADR-0023) and the Q&A threads under each lecture. Design:
 * docs/lld/engagement.md.
 *
 * <p>No public API: a leaf module. It calls catalog's API (facts, {@code updateRating}),
 * enrollment's ({@code isEnrolled}) and identity's (display names); nobody calls it.
 */
package com.masternova.api.engagement;
