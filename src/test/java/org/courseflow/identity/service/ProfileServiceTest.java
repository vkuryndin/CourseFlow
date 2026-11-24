package org.courseflow.identity.service;

import static org.courseflow.testing.Fixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.dto.UpdateProfileRequest;
import org.courseflow.identity.entity.Profile;
import org.courseflow.identity.repository.ProfileRepository;
import org.courseflow.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ProfileServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final ProfileService service = new ProfileService(profiles, users);

    @Test
    void missingUserRejected() {
        assertThrows(ResourceNotFoundException.class, () -> service.getMyProfile(10L));
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateMyProfile(10L, new UpdateProfileRequest(null, null)));
    }

    @Test
    void missingProfileReturnsEmptyResponse() {
        when(users.findById(10L)).thenReturn(Optional.of(student()));

        var response = service.getMyProfile(10L);

        assertNull(response.bio());
        assertNull(response.avatarUrl());
    }

    @Test
    void existingProfileReturned() {
        var s = student();
        Profile p = new Profile(s);
        p.update("Bio", "https://example.test");
        when(users.findById(10L)).thenReturn(Optional.of(s));
        when(profiles.findByUser_Id(10L)).thenReturn(Optional.of(p));

        assertEquals("Bio", service.getMyProfile(10L).bio());
    }

    @Test
    void createProfileNormalizesFields() {
        when(users.findByIdForUpdate(10L)).thenReturn(Optional.of(student()));
        when(profiles.save(any(Profile.class))).thenAnswer((i) -> i.getArgument(0));
        var r = service.updateMyProfile(10L, new UpdateProfileRequest(" Bio ", " https://example.test "));

        assertEquals("Bio", r.bio());
        assertEquals("https://example.test", r.avatarUrl());
    }

    @Test
    void updateExistingProfileClearsBlankValues() {
        var s = student();
        Profile p = new Profile(s);
        p.update("Bio", "https://example.test");
        when(users.findByIdForUpdate(10L)).thenReturn(Optional.of(s));
        when(profiles.findByUser_Id(10L)).thenReturn(Optional.of(p));
        when(profiles.save(p)).thenReturn(p);
        var r = service.updateMyProfile(10L, new UpdateProfileRequest(" ", null));

        assertNull(r.bio());
        assertNull(r.avatarUrl());
        verify(profiles).save(p);
    }

    @Test
    void inactiveUserAndInvalidUrlRejectedBeforeWrite() {
        var s = student();
        s.deactivate();
        when(users.findById(10L)).thenReturn(Optional.of(s));

        assertThrows(AccessDeniedException.class, () -> service.getMyProfile(10L));
        when(users.findByIdForUpdate(10L)).thenReturn(Optional.of(student()));
        assertThrows(BadRequestException.class,
                () -> service.updateMyProfile(10L, new UpdateProfileRequest("Bio", "javascript:bad")));
        verifyNoInteractions(profiles);
    }
}
