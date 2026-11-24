package org.courseflow.identity.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.courseflow.common.api.PageResponse;
import org.courseflow.identity.dto.AdminCreateUserRequest;
import org.courseflow.identity.dto.UpdateUserRequest;
import org.courseflow.identity.dto.UpdateUserRolesRequest;
import org.courseflow.identity.dto.UserResponse;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.identity.service.AdminUserService;
import org.courseflow.identity.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Direct controller contract tests; no Spring MVC runtime.
 */
class UserControllerTest {
    private final UserService userService = mock(UserService.class);
    private final AdminUserService adminUserService = mock(AdminUserService.class);
    private final UserController controller = new UserController(userService, adminUserService);

    @Test
    void getMeDelegatesAndReturnsContract() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        UserResponse expected = mock(UserResponse.class);
        when(userService.getById(42L)).thenReturn(expected);

        var actual = controller.getMe(jwt);

        assertSame(expected, actual);
        verify(userService).getById(42L);
    }

    @Test
    void getByIdDelegatesAndReturnsContract() {
        Long id = 101L;
        UserResponse expected = mock(UserResponse.class);
        when(userService.getById(id)).thenReturn(expected);

        var actual = controller.getById(id);

        assertSame(expected, actual);
        verify(userService).getById(id);
    }

    @Test
    void findAllDelegatesAndReturnsContract() {
        int page = 1;
        int size = 15;
        String q = "search";
        UserStatus status = null;
        UserRole role = null;
        String sortBy = "id";
        String direction = "desc";
        PageResponse<UserResponse> expected = new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
        when(userService.findAll(page, size, q, status, role, sortBy, direction)).thenReturn(expected);

        var actual = controller.findAll(page, size, q, status, role, sortBy, direction);

        assertSame(expected, actual);
        verify(userService).findAll(page, size, q, status, role, sortBy, direction);
    }

    @Test
    void createDelegatesAndReturnsContract() {
        AdminCreateUserRequest request = mock(AdminCreateUserRequest.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        UserResponse expected = mock(UserResponse.class);
        when(expected.id()).thenReturn(999L);
        when(adminUserService.create(request)).thenReturn(expected);

        var actual = controller.create(request, jwt);

        assertEquals(201, actual.getStatusCode().value());
        assertSame(expected, actual.getBody());
        assertEquals("/api/users/999", actual.getHeaders().getLocation().toString());
        verify(adminUserService).create(request);
    }

    @Test
    void updateRolesDelegatesAndReturnsContract() {
        Long id = 101L;
        UpdateUserRolesRequest request = mock(UpdateUserRolesRequest.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        UserResponse expected = mock(UserResponse.class);
        when(adminUserService.updateRoles(id, request)).thenReturn(expected);

        var actual = controller.updateRoles(id, request, jwt);

        assertSame(expected, actual);
        verify(adminUserService).updateRoles(id, request);
    }

    @Test
    void updateDetailsDelegatesAndReturnsContract() {
        Long id = 101L;
        UpdateUserRequest request = mock(UpdateUserRequest.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        UserResponse expected = mock(UserResponse.class);
        when(adminUserService.updateDetails(id, request)).thenReturn(expected);

        var actual = controller.updateDetails(id, request, jwt);

        assertSame(expected, actual);
        verify(adminUserService).updateDetails(id, request);
    }

    @Test
    void deactivateDelegatesAndReturnsContract() {
        Long id = 101L;
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        UserResponse expected = mock(UserResponse.class);
        when(adminUserService.deactivate(id)).thenReturn(expected);

        var actual = controller.deactivate(id, jwt);

        assertSame(expected, actual);
        verify(adminUserService).deactivate(id);
    }

    @Test
    void activateDelegatesAndReturnsContract() {
        Long id = 101L;
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");
        UserResponse expected = mock(UserResponse.class);
        when(adminUserService.activate(id)).thenReturn(expected);

        var actual = controller.activate(id, jwt);

        assertSame(expected, actual);
        verify(adminUserService).activate(id);
    }

    @Test
    void deleteDelegatesAndReturnsContract() {
        Long id = 101L;
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("42");

        var actual = controller.delete(id, jwt);

        assertEquals(204, actual.getStatusCode().value());
        assertNull(actual.getBody());
        verify(adminUserService).deleteUser(id);
    }
}
