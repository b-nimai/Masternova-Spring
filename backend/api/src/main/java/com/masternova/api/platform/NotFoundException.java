package com.masternova.api.platform;

import java.util.Map;

/**
 * 404 — the resource doesn't exist OR the caller may not see it. Deliberately the same answer, so
 * an endpoint can't be used to probe which ids exist (API conventions §1).
 */
public final class NotFoundException extends DomainException {

  public NotFoundException(String resource, Object id) {
    super("NOT_FOUND", resource + " " + id + " was not found", Map.of("resource", resource));
  }
}
