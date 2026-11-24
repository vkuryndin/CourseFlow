package org.courseflow.identity.service;

import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.dto.ProfileResponse;
import org.courseflow.identity.dto.UpdateProfileRequest;
import org.courseflow.identity.entity.Profile;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.identity.repository.ProfileRepository;
import org.courseflow.identity.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.courseflow.common.validation.HttpUrls;

@Service
public class ProfileService {
    private final ProfileRepository profiles;
    private final UserRepository users;

    public ProfileService(ProfileRepository profiles, UserRepository users) {
        this.profiles = profiles;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getMyProfile(Long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        requireActive(user);
        return profiles.findByUser_Id(userId)
                .map(p -> response(userId, p))
                .orElseGet(() -> new ProfileResponse(userId, null, null));
    }

    @Transactional
    public ProfileResponse updateMyProfile(Long userId, UpdateProfileRequest request) {
        // Same user lock as administrative anonymization; serializes first creation.
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        requireActive(user);
        String avatar = HttpUrls.optional(request.avatarUrl(), "avatarUrl");
        Profile profile = profiles.findByUser_Id(userId).orElseGet(() -> new Profile(user));
        profile.update(normalize(request.bio()), avatar);
        return response(userId, profiles.save(profile));
    }

    private void requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedException("User is not active");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ProfileResponse response(Long userId, Profile profile) {
        return new ProfileResponse(userId, profile.getBio(), profile.getAvatarUrl());
    }
}
