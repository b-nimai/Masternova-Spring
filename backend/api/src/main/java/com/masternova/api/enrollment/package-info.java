/**
 * Enrollment module — a learner's courses ("My Learning") and where they stopped in every lecture.
 * Enrollment follows entitlement by events (an Observer, durable through Modulith's registry:
 * ADR-0021); progress heartbeats go through a Redis write-back buffer flushed every 30 s
 * (ADR-0022). Design: docs/lld/progress.md.
 *
 * <p>No public API: a leaf module. It listens to entitlement's events and calls catalog's and
 * entitlement's APIs; nobody calls it.
 */
package com.masternova.api.enrollment;
