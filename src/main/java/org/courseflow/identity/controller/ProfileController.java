package org.courseflow.identity.controller;

import org.courseflow.security.CurrentUser;
import jakarta.validation.Valid;
import org.courseflow.identity.dto.ProfileResponse;
import org.courseflow.identity.dto.UpdateProfileRequest;
import org.courseflow.identity.service.ProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me/profile")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return profileService.getMyProfile(CurrentUser.id(jwt));
    }

    @PutMapping
    public ProfileResponse update(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateMyProfile(CurrentUser.id(jwt), request);
    }
}
