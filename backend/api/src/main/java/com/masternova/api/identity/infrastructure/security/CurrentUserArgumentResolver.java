package com.masternova.api.identity.infrastructure.security;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.Role;
import com.masternova.api.platform.UnauthenticatedException;
import java.util.List;
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
 */
class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.getParameterType().equals(CurrentUser.class);
  }

  @Override
  public CurrentUser resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mav,
      NativeWebRequest request,
      WebDataBinderFactory binders) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof JwtAuthenticationToken jwt)) {
      throw new UnauthenticatedException("UNAUTHENTICATED", "Authentication is required.");
    }
    List<String> roles = jwt.getToken().getClaimAsStringList("roles");
    return new CurrentUser(
        UUID.fromString(jwt.getToken().getSubject()),
        roles == null
            ? java.util.Set.of()
            : roles.stream().map(Role::valueOf).collect(Collectors.toSet()),
        Boolean.TRUE.equals(jwt.getToken().getClaimAsBoolean("email_verified")));
  }
}
