/**
 * Notification module (api side) — the user's CONSENT: per-category email preferences and the
 * public unsubscribe endpoints. Sending lives in the worker; both read the same tables and share
 * the kernel's {@code NotificationCategory} and {@code UnsubscribeTokens}. Design:
 * docs/lld/notification.md.
 *
 * <p>Public API: none — no other module calls it. Its REST endpoints are its interface.
 */
package com.masternova.api.notification;
