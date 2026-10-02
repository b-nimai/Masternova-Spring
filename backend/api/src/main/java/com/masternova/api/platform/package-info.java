/**
 * Platform module — cross-cutting plumbing every bounded context relies on: error envelope,
 * security wiring, clock, service metadata. From Phase 2 it also owns the transactional outbox and
 * idempotency keys.
 */
package com.masternova.api.platform;
