/**
 * Identity module — accounts, authentication (JWT access + rotating refresh tokens) and role-based
 * authorisation. Owns the API's security filter chain. Design: docs/lld/identity.md.
 *
 * <p>Public API (this package): {@link com.masternova.api.identity.Role}, {@link
 * com.masternova.api.identity.UserRegistered}, {@link com.masternova.api.identity.EmailVerified},
 * {@link com.masternova.api.identity.CurrentUser}.
 */
package com.masternova.api.identity;
