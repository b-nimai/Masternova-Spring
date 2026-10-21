package com.masternova.api.catalog.infrastructure.search;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code masternova.search.embedding.*} — semantic search (ADR-0024 §6). Off unless enabled: the
 * cluster and CI have no model, and keyword search needs none.
 *
 * @param maxDistance cosine distance (0 = same direction, 2 = opposite) beyond which a course is
 *     not "about" the query at all; nearest-neighbour search always returns SOMETHING otherwise
 */
@ConfigurationProperties("masternova.search.embedding")
record SearchProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("http://localhost:11434") String baseUrl,
    @DefaultValue("nomic-embed-text") String model,
    @DefaultValue("2s") Duration queryTimeout,
    @DefaultValue("30s") Duration breakerOpenFor,
    @DefaultValue("0.5") double maxDistance,
    @DefaultValue("32") int batchSize) {}
