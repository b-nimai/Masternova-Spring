/**
 * Catalog module — what a course IS (the Course → Section → Lecture aggregate, its category and
 * price) and how courses are browsed, filtered, paged and duplicated. Authoring (lifecycle, publish
 * gate, curriculum edits) arrives in Phase 6. Design: docs/lld/catalog.md.
 *
 * <p>Public API: {@link com.masternova.api.catalog.CatalogApi} returning {@link
 * com.masternova.api.catalog.CourseFacts} (Phase 8: entitlement decides from these facts) and
 * {@link com.masternova.api.catalog.CourseOffer} (Phase 9: commerce prices carts from these).
 */
package com.masternova.api.catalog;
