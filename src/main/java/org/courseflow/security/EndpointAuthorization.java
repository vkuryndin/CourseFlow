package org.courseflow.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Ordered endpoint policy. Specific routes must remain before overlapping wildcard routes. */
final class EndpointAuthorization {
  private EndpointAuthorization() {}

  static void configure(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth,
      boolean apiDocsEnabled) {
    if (apiDocsEnabled) {
      auth.requestMatchers(
              HttpMethod.GET,
              "/v3/api-docs",
              "/v3/api-docs.yaml",
              "/v3/api-docs/swagger-config",
              "/swagger-ui.html",
              "/swagger-ui/**")
          .permitAll();
    }
    // Only the three status-only probes are public; no wildcard actuator access.
    auth.requestMatchers(
            HttpMethod.GET,
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness")
        .permitAll();
    // A temporary-password JWT can only change its password or log out.
    auth.requestMatchers(
            request -> {
              var authentication = SecurityContextHolder.getContext().getAuthentication();
              if (!(authentication instanceof JwtAuthenticationToken token)
                  || !Boolean.TRUE.equals(
                      token.getToken().getClaimAsBoolean("passwordChangeRequired"))) {
                return false;
              }
              String path = request.getRequestURI().substring(request.getContextPath().length());
              return !("POST".equals(request.getMethod())
                  && ("/api/auth/change-password".equals(path) || "/api/auth/logout".equals(path)));
            })
        .denyAll();
    infrastructure(auth);
    profileAndLearning(auth);
    categoryChanges(auth);
    dashboardAndReviews(auth);
    assessment(auth);
    identity(auth);
    catalog(auth);
    content(auth);
    enrollmentAndAssignments(auth);
    authenticatedSystem(auth);
    auth.anyRequest().denyAll();
  }

  private static void infrastructure(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          auth) {
    auth.requestMatchers(HttpMethod.GET, "/actuator/metrics", "/actuator/metrics/*")
        .hasRole("ADMIN");
    auth.requestMatchers(HttpMethod.GET, PublicEndpoints.getPatterns()).permitAll();
    auth.requestMatchers(HttpMethod.POST, PublicEndpoints.postPatterns()).permitAll();
  }

  private static void profileAndLearning(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          auth) {
    auth.requestMatchers(HttpMethod.GET, "/api/users/me/profile").authenticated();
    auth.requestMatchers(HttpMethod.PUT, "/api/users/me/profile").authenticated();
    auth.requestMatchers(
            HttpMethod.GET,
            "/api/users/me/assignments",
            "/api/users/me/submissions",
            "/api/users/me/quiz-attempts")
        .hasRole("STUDENT");
  }

  private static void categoryChanges(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          auth) {
    auth.requestMatchers(HttpMethod.PUT, "/api/categories/*").hasRole("ADMIN");
    auth.requestMatchers(HttpMethod.DELETE, "/api/categories/*").hasRole("ADMIN");
  }

  private static void dashboardAndReviews(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          auth) {
    auth.requestMatchers(HttpMethod.GET, "/api/dashboard/student").hasRole("STUDENT");
    auth.requestMatchers(HttpMethod.GET, "/api/dashboard/teacher").hasRole("TEACHER");
    auth.requestMatchers(HttpMethod.GET, "/api/dashboard/admin").hasRole("ADMIN");
    auth.requestMatchers(HttpMethod.GET, "/api/users/me/progress").hasRole("STUDENT");
    auth.requestMatchers(HttpMethod.GET, "/api/courses/*/gradebook").hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.POST, "/api/courses/*/reviews").hasRole("STUDENT");
    auth.requestMatchers(HttpMethod.GET, "/api/courses/*/reviews/me").hasRole("STUDENT");
    auth.requestMatchers(HttpMethod.PUT, "/api/courses/*/reviews/me").hasRole("STUDENT");
    auth.requestMatchers(HttpMethod.DELETE, "/api/courses/*/reviews/me").hasRole("STUDENT");
  }

  private static void assessment(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          auth) {
    auth.requestMatchers(
            HttpMethod.GET, "/api/quizzes/*/attempts", "/api/quizzes/*/attempts/*/manage")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(
            HttpMethod.GET, "/api/quizzes/*/attempts/me", "/api/quizzes/*/attempts/*/result")
        .hasRole("STUDENT");
    auth.requestMatchers(HttpMethod.POST, "/api/quizzes/*/attempts/*/submit").hasRole("STUDENT");
    auth.requestMatchers(HttpMethod.POST, "/api/quizzes/*/attempts").hasRole("STUDENT");
    auth.requestMatchers(HttpMethod.GET, "/api/quizzes/*/attempts/*").hasRole("STUDENT");
    auth.requestMatchers(HttpMethod.PUT, "/api/quizzes/*", "/api/quizzes/*/questions/*")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.DELETE, "/api/quizzes/*", "/api/quizzes/*/questions/*")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.POST, "/api/quizzes/*/questions")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.GET, "/api/quizzes/*/questions/manage")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.GET, "/api/quizzes/*/questions").authenticated();
    auth.requestMatchers(HttpMethod.POST, "/api/modules/*/quiz").hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.GET, "/api/modules/*/quiz", "/api/quizzes/*").authenticated();
  }

  private static void identity(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          auth) {
    auth.requestMatchers(HttpMethod.GET, "/api/users/me").authenticated();
    auth.requestMatchers(HttpMethod.GET, "/api/users/me/enrollments").hasRole("STUDENT");
    auth.requestMatchers(HttpMethod.GET, "/api/users", "/api/users/*").hasRole("ADMIN");
    auth.requestMatchers(
            HttpMethod.POST,
            "/api/users",
            "/api/users/*/activate",
            "/api/users/*/deactivate",
            "/api/users/*/password-reset")
        .hasRole("ADMIN");
    auth.requestMatchers(HttpMethod.PUT, "/api/users/*", "/api/users/*/roles").hasRole("ADMIN");
    auth.requestMatchers(HttpMethod.DELETE, "/api/users/*").hasRole("ADMIN");
  }

  private static void catalog(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          auth) {
    auth.requestMatchers(HttpMethod.POST, "/api/categories").hasRole("ADMIN");
    auth.requestMatchers(HttpMethod.POST, "/api/courses").hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.PUT, "/api/courses/*/teacher").hasRole("ADMIN");
    auth.requestMatchers(HttpMethod.PUT, "/api/courses/*").hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.POST, "/api/courses/*/archive").hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.DELETE, "/api/courses/*").hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.POST, "/api/tags").hasRole("ADMIN");
    auth.requestMatchers(HttpMethod.PUT, "/api/tags/*").hasRole("ADMIN");
    auth.requestMatchers(HttpMethod.DELETE, "/api/tags/*").hasRole("ADMIN");
    auth.requestMatchers(HttpMethod.POST, "/api/courses/*/tags/*").hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.DELETE, "/api/courses/*/tags/*").hasAnyRole("ADMIN", "TEACHER");
  }

  private static void content(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          auth) {
    auth.requestMatchers(HttpMethod.POST, "/api/courses/*/modules").hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.PUT, "/api/courses/*/modules/*").hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.PATCH, "/api/courses/*/modules/*/position")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.DELETE, "/api/courses/*/modules/*")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(
            HttpMethod.GET,
            "/api/courses/*/modules/*/lessons",
            "/api/courses/*/modules/*/lessons/*")
        .authenticated();
    auth.requestMatchers(HttpMethod.POST, "/api/courses/*/modules/*/lessons")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.PUT, "/api/courses/*/modules/*/lessons/*")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.PATCH, "/api/courses/*/modules/*/lessons/*/position")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.DELETE, "/api/courses/*/modules/*/lessons/*")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.GET, "/api/lessons/*/sessions", "/api/lessons/*/sessions/*")
        .authenticated();
    auth.requestMatchers(HttpMethod.POST, "/api/lessons/*/sessions").hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.PUT, "/api/lessons/*/sessions/*")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.PATCH, "/api/lessons/*/sessions/*/time")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.DELETE, "/api/lessons/*/sessions/*")
        .hasAnyRole("ADMIN", "TEACHER");
  }

  private static void enrollmentAndAssignments(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          auth) {
    auth.requestMatchers(HttpMethod.POST, "/api/courses/*/enrollments").hasRole("STUDENT");
    auth.requestMatchers(HttpMethod.GET, "/api/courses/*/enrollments/me").hasRole("STUDENT");
    auth.requestMatchers(HttpMethod.DELETE, "/api/courses/*/enrollments/me").hasRole("STUDENT");
    auth.requestMatchers(HttpMethod.DELETE, "/api/courses/*/enrollments/students/*")
        .hasRole("ADMIN");
    auth.requestMatchers(HttpMethod.GET, "/api/courses/*/enrollments")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.POST, "/api/lessons/*/assignments")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.PUT, "/api/lessons/*/assignments/*")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.PATCH, "/api/lessons/*/assignments/*/deadline")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.DELETE, "/api/lessons/*/assignments/*")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(
            HttpMethod.GET, "/api/lessons/*/assignments", "/api/lessons/*/assignments/*")
        .authenticated();
    auth.requestMatchers(HttpMethod.POST, "/api/assignments/*/submissions").hasRole("STUDENT");
    auth.requestMatchers(HttpMethod.GET, "/api/assignments/*/submissions/me").hasRole("STUDENT");
    auth.requestMatchers(HttpMethod.GET, "/api/assignments/*/submissions")
        .hasAnyRole("ADMIN", "TEACHER");
    auth.requestMatchers(HttpMethod.PUT, "/api/assignments/*/submissions/*/grade")
        .hasAnyRole("ADMIN", "TEACHER");
  }

  private static void authenticatedSystem(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          auth) {
    auth.requestMatchers(HttpMethod.POST, "/api/auth/logout", "/api/auth/change-password")
        .authenticated();
    auth.requestMatchers(HttpMethod.GET, "/api/system/status").authenticated();
    auth.requestMatchers(HttpMethod.GET, "/api/system/info").hasRole("ADMIN");
  }
}
