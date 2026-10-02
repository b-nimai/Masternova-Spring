/**
 * Catalog module — what a course IS (the Course → Section → Lecture aggregate, its category and
 * price) and how courses are browsed, filtered, paged and duplicated. Authoring (lifecycle, publish
 * gate, curriculum edits) arrives in Phase 6. Design: docs/lld/catalog.md.
 *
 * <p>Public API: none yet — no other module calls catalog. Its REST endpoints are its interface.
 */
package com.masternova.api.catalog;
