package org.courseflow.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.courseflow.auth.api.SessionAccess;
import org.junit.jupiter.api.Test;

class PublicEndpointsTest {

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
