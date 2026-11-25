package org.courseflow.security;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.util.matcher.RequestMatcher;

class SystemInfoAuthorizationTest {
  @Test
  @SuppressWarnings("unchecked")
  void registersDedicatedAdminMatcherAndPreservesStatusPolicy() {
    AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
        registry =
            mock(AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry.class);
    AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl general =
        mock(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class);
    AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl diagnostics =
        mock(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class);
    AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl status =
        mock(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class);
    when(registry.requestMatchers(any(HttpMethod.class), any(String[].class))).thenReturn(general);
    when(registry.requestMatchers(any(RequestMatcher[].class))).thenReturn(general);
    when(registry.anyRequest()).thenReturn(general);
    when(registry.requestMatchers(HttpMethod.GET, "/api/system/info")).thenReturn(diagnostics);
    when(registry.requestMatchers(HttpMethod.GET, "/api/system/status")).thenReturn(status);
    EndpointAuthorization.configure(registry, false);
    verify(diagnostics).hasRole("ADMIN");
    verify(status).authenticated();
  }
}
