package org.courseflow.identity.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.courseflow.identity.dto.ProfileResponse;
import org.courseflow.identity.dto.UpdateProfileRequest;
import org.courseflow.identity.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** Direct controller contract tests; no Spring MVC runtime. */
class ProfileControllerTest {
  private final ProfileService profileService = mock(ProfileService.class);
  private final ProfileController controller = new ProfileController(profileService);

  @Test
  void getDelegatesAndReturnsContract() {
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    ProfileResponse expected = mock(ProfileResponse.class);
    when(profileService.getMyProfile(42L)).thenReturn(expected);

    var actual = controller.get(jwt);

    assertSame(expected, actual);
    verify(profileService).getMyProfile(42L);
  }

  @Test
  void updateDelegatesAndReturnsContract() {
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("42");
    UpdateProfileRequest request = mock(UpdateProfileRequest.class);
    ProfileResponse expected = mock(ProfileResponse.class);
    when(profileService.updateMyProfile(42L, request)).thenReturn(expected);

    var actual = controller.update(jwt, request);

    assertSame(expected, actual);
    verify(profileService).updateMyProfile(42L, request);
  }
}
