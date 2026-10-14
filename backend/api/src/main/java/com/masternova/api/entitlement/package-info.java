/**
 * Entitlement module — decides whether a viewer may play a lecture (a policy chain where an
 * explicit denial always wins), owns the entitlement rows that commerce grants and revokes, and
 * issues the short-lived, IP-bound playback tokens for learners. Design:
 * docs/lld/entitlement-engine.md.
 *
 * <p>Public API: {@link com.masternova.api.entitlement.EntitlementApi} with {@link
 * com.masternova.api.entitlement.AccessDecision}, and the {@code @entitlements} bean for
 * {@code @PreAuthorize("@entitlements.canWatch(authentication, #lectureId)")}.
 */
package com.masternova.api.entitlement;
