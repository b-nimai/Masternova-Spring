package com.masternova.api.identity.infrastructure.security;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.Role;
import com.masternova.api.platform.UnauthenticatedException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets controllers declare {@code CurrentUser me} — Spring MVC calls this to produce the argument
 * (note 10 §1, "argument resolution"). Built from the JWT already verified by the filter chain.
 *
 * <p>Two forms:
 *
 * <ul>
 *   <li>{@code CurrentUser me}: authentication required — anonymous callers get a 401.
 *   <li>{@code Optional<CurrentUser> viewer}: for PUBLIC routes that answer differently for a
 *       signed-in caller (catalog: an owner sees their own draft). The security chain still
 *       verifies a bearer token on a public route when one is sent; no token means empty. ({@code
 *       Optional} as a parameter is normally avoided — note 05 §9 — but Spring MVC's own arguments
 *       use it exactly this way, as "may be absent".)
 * </ul>
 */
class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    // nestedIfOptional(): for Optional<CurrentUser>, look at the type INSIDE the Optional
    return parameter.nestedIfOptional().getNestedParameterType().equals(CurrentUser.class);
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mav,
      NativeWebRequest request,
      WebDataBinderFactory binders) {
    Optional<CurrentUser> user = current();
    if (parameter.getParameterType() == Optional.class) {
      return user;
    }
    return user.orElseThrow(
        () -> new UnauthenticatedException("UNAUTHENTICATED", "Authentication is required."));
  }

  private static Optional<CurrentUser> current() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof JwtAuthenticationToken jwt)) {
      return Optional.empty(); // anonymous: no (valid) bearer token on a public route
    }
    List<String> roles = jwt.getToken().getClaimAsStringList("roles");
    return Optional.of(
        new CurrentUser(
            UUID.fromString(jwt.getToken().getSubject()),
            roles == null
                ? java.util.Set.of()
                : roles.stream().map(Role::valueOf).collect(Collectors.toSet()),
            Boolean.TRUE.equals(jwt.getToken().getClaimAsBoolean("email_verified"))));
  }
}
