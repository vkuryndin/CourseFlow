package org.courseflow.identity.service;

import static org.courseflow.testing.Fixtures.student;
import static org.courseflow.testing.Fixtures.teacher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Optional;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class UserServiceTest {
  private final UserRepository repository = mock(UserRepository.class);
  private final UserService service = new UserService(repository);

  @SuppressWarnings("unchecked")
  private void stubPage(List<User> users) {
    when(repository.findAll(any(Specification.class), any(Pageable.class)))
        .thenAnswer(invocation -> new PageImpl<>(users, invocation.getArgument(1), users.size()));
  }

  @SuppressWarnings("unchecked")
  private Specification<User> capturedSpecification() {
    ArgumentCaptor<Specification<User>> captor =
        ArgumentCaptor.forClass((Class) Specification.class);
    verify(repository).findAll(captor.capture(), any(Pageable.class));
    return captor.getValue();
  }

  @Test
  void getUserMapsRoles() {
    when(repository.findById(10L)).thenReturn(Optional.of(student()));
    assertEquals(java.util.Set.of(UserRole.STUDENT), service.getById(10L).roles());
  }

  @Test
  void missingUserRejected() {
    assertThrows(ResourceNotFoundException.class, () -> service.getById(10L));
  }

  @Test
  void publicTeacherReturnsName() {
    when(repository.findById(20L)).thenReturn(Optional.of(teacher()));
    assertEquals("Teacher", service.getPublicTeacher(20L).displayName());
  }

  @Test
  void studentIsNotPublicTeacher() {
    when(repository.findById(10L)).thenReturn(Optional.of(student()));
    assertThrows(ResourceNotFoundException.class, () -> service.getPublicTeacher(10L));
  }

  @Test
  void inactiveTeacherIsNotPublic() {
    var user = teacher();
    user.deactivate();
    when(repository.findById(20L)).thenReturn(Optional.of(user));
    assertThrows(ResourceNotFoundException.class, () -> service.getPublicTeacher(20L));
  }

  @Test
  void publicDirectoryMapsOnlyPublicFieldsAndUsesStableSort() {
    stubPage(List.of(teacher()));
    var result = service.findPublicTeachers(0, 20, "Teacher");
    assertEquals(20L, result.content().get(0).id());
    assertEquals("Teacher", result.content().get(0).displayName());
    ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findAll(any(Specification.class), page.capture());
    assertEquals(
        org.springframework.data.domain.Sort.by("displayName", "id"), page.getValue().getSort());
    verify(repository, never()).findWithRolesByIds(any());
  }

  @Test
  void publicDirectoryRejectsInvalidParameters() {
    assertThrows(
        org.courseflow.common.exception.BadRequestException.class,
        () -> service.findPublicTeachers(-1, 20, ""));
    assertThrows(
        org.courseflow.common.exception.BadRequestException.class,
        () -> service.findPublicTeachers(0, 101, ""));
    assertThrows(
        org.courseflow.common.exception.BadRequestException.class,
        () -> service.findPublicTeachers(0, 20, "x".repeat(121)));
    verifyNoInteractions(repository);
  }

  @Test
  void emptyPageDoesNotFetchRoles() {
    stubPage(List.of());
    assertTrue(service.findAll(0, 10, null, null, null, "id", "asc").content().isEmpty());
    verify(repository, never()).findWithRolesByIds(any());
    @SuppressWarnings("unchecked")
    Root<User> root = mock(Root.class);
    CriteriaBuilder builder = mock(CriteriaBuilder.class);
    capturedSpecification().toPredicate(root, mock(CriteriaQuery.class), builder);
    verify(builder).and(new Predicate[0]);
  }

  @Test
  void filtersAndRoleBatchFetch() {
    stubPage(List.of(student()));
    var result =
        service.findAll(0, 10, " Student ", UserStatus.ACTIVE, UserRole.STUDENT, "email", "desc");
    assertEquals(10L, result.content().get(0).id());
    verify(repository).findWithRolesByIds(List.of(10L));
    @SuppressWarnings("unchecked")
    Root<User> root = mock(Root.class);
    CriteriaBuilder builder = mock(CriteriaBuilder.class);
    @SuppressWarnings("unchecked")
    Path<String> name = mock(Path.class);
    @SuppressWarnings("unchecked")
    Path<String> email = mock(Path.class);
    @SuppressWarnings("unchecked")
    Path<UserStatus> statusPath = mock(Path.class);
    @SuppressWarnings("unchecked")
    Join<User, UserRole> roles = mock(Join.class);
    @SuppressWarnings("unchecked")
    Expression<String> nameLower = mock(Expression.class);
    @SuppressWarnings("unchecked")
    Expression<String> emailLower = mock(Expression.class);
    when(root.<String>get("displayName")).thenReturn(name);
    when(root.<String>get("email")).thenReturn(email);
    when(root.<UserStatus>get("status")).thenReturn(statusPath);
    when(root.<User, UserRole>join("roles", JoinType.INNER)).thenReturn(roles);
    when(builder.lower(name)).thenReturn(nameLower);
    when(builder.lower(email)).thenReturn(emailLower);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    capturedSpecification().toPredicate(root, query, builder);
    verify(query).distinct(true);
    verify(root).join("roles", JoinType.INNER);
    verify(builder).equal(statusPath, UserStatus.ACTIVE);
    verify(builder).equal(roles, UserRole.STUDENT);
    verify(builder).like(nameLower, "%student%", '\\');
    verify(builder).like(emailLower, "%student%", '\\');
  }

  @Test
  void blankSearchDoesNotFilter() {
    stubPage(List.of());
    service.findAll(0, 10, "   ", null, null, "id", "asc");
    @SuppressWarnings("unchecked")
    Root<User> root = mock(Root.class);
    CriteriaBuilder builder = mock(CriteriaBuilder.class);
    capturedSpecification().toPredicate(root, mock(CriteriaQuery.class), builder);
    verifyNoInteractions(root);
  }
}
