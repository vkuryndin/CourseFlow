package org.courseflow.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.util.matcher.RequestMatcher;

class EndpointAuthorizationTest {
  @SuppressWarnings("unchecked")
  private final AuthorizeHttpRequestsConfigurer<HttpSecurity>
          .AuthorizationManagerRequestMatcherRegistry
      registry =
          mock(AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry.class);

  @SuppressWarnings("unchecked")
  private final AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl url =
      mock(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class);

  private void stubMatchers() {
    when(registry.requestMatchers(any(HttpMethod.class), any(String[].class))).thenReturn(url);
    when(registry.requestMatchers(any(RequestMatcher[].class))).thenReturn(url);
    when(registry.anyRequest()).thenReturn(url);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void registersPublicHealthProtectedGradingAndDefaultDeny() {
    stubMatchers();
    @SuppressWarnings("unchecked")
    AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl fallback =
        mock(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class);
    when(registry.anyRequest()).thenReturn(fallback);
    @SuppressWarnings("unchecked")
    AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl grading =
        mock(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class);
    when(registry.requestMatchers(HttpMethod.PUT, "/api/assignments/*/submissions/*/grade"))
        .thenReturn(grading);

    EndpointAuthorization.configure(registry, false);

    verify(registry)
        .requestMatchers(
            HttpMethod.GET,
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness");
    verify(grading).hasAnyRole("ADMIN", "TEACHER");
    verify(fallback).denyAll();
    verify(registry, never())
        .requestMatchers(
            HttpMethod.GET,
            "/v3/api-docs",
            "/v3/api-docs.yaml",
            "/v3/api-docs/swagger-config",
            "/swagger-ui.html",
            "/swagger-ui/**");
  }

  @Test
  @SuppressWarnings("unchecked")
  void criticalRoutesKeepTheirOwnAuthorization() {
    stubMatchers();
    var admin =
        (AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl)
            mock(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class);
    var student =
        (AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl)
            mock(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class);
    var teacher =
        (AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl)
            mock(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class);
    var authenticated =
        (AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl)
            mock(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class);
    when(registry.requestMatchers(HttpMethod.GET, "/api/system/info")).thenReturn(admin);
    when(registry.requestMatchers(HttpMethod.POST, "/api/courses/*/enrollments"))
        .thenReturn(student);
    when(registry.requestMatchers(HttpMethod.GET, "/api/courses/*/gradebook")).thenReturn(teacher);
    when(registry.requestMatchers(HttpMethod.GET, "/api/users/me")).thenReturn(authenticated);
    EndpointAuthorization.configure(registry, false);
    verify(admin).hasRole("ADMIN");
    verify(student).hasRole("STUDENT");
    verify(teacher).hasAnyRole("ADMIN", "TEACHER");
    verify(authenticated).authenticated();
    org.mockito.Mockito.verifyNoMoreInteractions(admin, student, teacher, authenticated);
  }

  @Test
  void swaggerRouteIsRegisteredOnlyWhenEnabled() {
    stubMatchers();
    @SuppressWarnings("unchecked")
    AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl docs =
        mock(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class);
    when(registry.requestMatchers(
            HttpMethod.GET,
            "/v3/api-docs",
            "/v3/api-docs.yaml",
            "/v3/api-docs/swagger-config",
            "/swagger-ui.html",
            "/swagger-ui/**"))
        .thenReturn(docs);
    EndpointAuthorization.configure(registry, true);
    verify(docs).permitAll();
  }

  @Test
  void temporaryPasswordMatcherDeniesAllExceptPasswordChangeAndLogout() {
    stubMatchers();
    EndpointAuthorization.configure(registry, false);
    var captor = ArgumentCaptor.forClass(RequestMatcher.class);
    verify(registry).requestMatchers(captor.capture());
    RequestMatcher matcher = captor.getValue();
    assertFalse(matcher.matches(new MockHttpServletRequest("GET", "/api/courses")));
    authenticate(false);
    assertFalse(matcher.matches(new MockHttpServletRequest("GET", "/api/courses")));
    authenticate(true);
    assertTrue(matcher.matches(new MockHttpServletRequest("GET", "/api/courses")));
    assertTrue(matcher.matches(new MockHttpServletRequest("GET", "/api/auth/change-password")));
    assertFalse(matcher.matches(new MockHttpServletRequest("POST", "/api/auth/change-password")));
    assertFalse(matcher.matches(new MockHttpServletRequest("POST", "/api/auth/logout")));
    var request = new MockHttpServletRequest("POST", "/app/api/auth/logout");
    request.setContextPath("/app");
    assertFalse(matcher.matches(request));
  }

  private void authenticate(boolean required) {
    var jwt =
        Jwt.withTokenValue("test")
            .header("alg", "HS256")
            .subject("10")
            .claim("passwordChangeRequired", required)
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }
}
