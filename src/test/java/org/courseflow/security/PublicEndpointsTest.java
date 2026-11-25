package org.courseflow.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.courseflow.auth.api.SessionAccess;
import org.courseflow.common.api.PublicEndpoints;
import org.junit.jupiter.api.Test;

class PublicEndpointsTest {

  @Test
  void publicRoutesMatchTheExplicitContract() {
    org.junit.jupiter.api.Assertions.assertEquals(
        java.util.Set.of(
            "/api/categories",
            "/api/categories/*",
            "/api/tags",
            "/api/tags/*",
            "/api/courses",
            "/api/courses/*",
            "/api/courses/*/tags",
            "/api/courses/*/modules",
            "/api/courses/*/modules/*",
            "/api/teachers",
            "/api/teachers/*",
            "/api/courses/*/reviews",
            "/api/courses/*/reviews/rating"),
        java.util.Set.of(PublicEndpoints.getPatterns()));
    org.junit.jupiter.api.Assertions.assertEquals(
        java.util.Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/forgot-password",
            "/api/auth/reset-password"),
        java.util.Set.of(PublicEndpoints.postPatterns()));
  }

  @Test
  void publicGetTemplatesAreMatchedWithoutExposingLessonContent() {
    assertTrue(PublicEndpoints.isPublicGet("/api/courses/{courseId}/modules/{moduleId}"));
    assertFalse(PublicEndpoints.isPublicGet("/api/courses/{courseId}/modules/{moduleId}/lessons"));
    assertFalse(PublicEndpoints.isPublicGet("/api/users/me"));
    assertFalse(PublicEndpoints.isPublicGet("/api/admin/users"));
  }

  @Test
  void passwordChangeRequiresAuthenticationButIsRateLimited() {
    assertTrue(PublicEndpoints.isPublicPost("/api/auth/login"));
    assertFalse(PublicEndpoints.isPublicPost("/api/auth/change-password"));
    assertTrue(PublicEndpoints.isRateLimitedPost("/api/auth/change-password"));
    assertFalse(PublicEndpoints.isRateLimitedPost("/api/auth/logout"));
  }

  @Test
  void startupRunnerDelegatesSessionInvalidation() {
    SessionAccess access = mock(SessionAccess.class);
    new AuthSessionStartupCleanup(access).run(null);

    verify(access).invalidateAllOnStartup();
  }
}
