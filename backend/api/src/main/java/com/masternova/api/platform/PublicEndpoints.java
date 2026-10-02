package com.masternova.api.platform;

import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpMethod;

/**
 * The API is DENY-BY-DEFAULT: every {@code /api/**} request needs a valid access token. A module
 * that owns an endpoint anyone may call declares it by contributing a bean of this type — the
 * security chain collects all of them. So no central list can forget an endpoint, and nothing is
 * public by accident.
 */
@FunctionalInterface
public interface PublicEndpoints {

  /** One public route; {@code method == null} means every HTTP method. */
  record Endpoint(HttpMethod method, String pattern) {
    public Endpoint {
      Objects.requireNonNull(pattern, "pattern");
    }

    public static Endpoint any(String pattern) {
      return new Endpoint(null, pattern);
    }

    public static Endpoint get(String pattern) {
      return new Endpoint(HttpMethod.GET, pattern);
    }
  }

  List<Endpoint> endpoints();
}
