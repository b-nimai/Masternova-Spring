/**
 * Enrollment module — a learner's courses ("My Learning") and where they stopped in every lecture.
 * Enrollment follows entitlement by events (an Observer, durable through Modulith's registry:
 * ADR-0021); progress heartbeats go through a Redis write-back buffer flushed every 30 s
 * (ADR-0022). Design: docs/lld/progress.md.
 *
 * <p>Public API: {@link com.masternova.api.enrollment.EnrollmentApi} ("is this learner enrolled?"),
 * since Phase 11 for engagement. It listens to entitlement's events and calls catalog's and
 * entitlement's APIs.
 */
package com.masternova.api.enrollment;
