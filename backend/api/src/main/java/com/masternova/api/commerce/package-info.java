/**
 * Commerce module — prices carts (coupons included), takes payments through a provider exactly once
 * in effect, and turns a paid order into course access in the same transaction; refunds take it
 * back. Design: docs/lld/order-state-machine.md · ADR-0018 · ADR-0019.
 *
 * <p>Public API: the events {@code OrderPaid} and {@code OrderRefunded} (through the outbox).
 * Nobody calls commerce, so there is no facade interface for other modules.
 */
package com.masternova.api.commerce;
