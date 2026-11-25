package org.courseflow.common.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import org.courseflow.assessment.dto.CreateQuestionRequest;
import org.courseflow.assessment.entity.QuestionType;
import org.courseflow.auth.dto.RegistrationRequest;
import org.courseflow.identity.dto.AdminCreateUserRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RequestDtoValidationTest {
  private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();

  @AfterEach
  void closeFactory() {
    factory.close();
  }

  private Set<String> invalidFields(Object request) {
    return factory.getValidator().validate(request).stream()
        .map(violation -> violation.getPropertyPath().toString())
        .collect(java.util.stream.Collectors.toSet());
  }

  @Test
  void validRegistrationAccepted() {
    assertTrue(
        invalidFields(new RegistrationRequest("Student", "student@example.test", "Valid-pass-1"))
            .isEmpty());
  }

  @Test
  void registrationRejectsInvalidEmailAndBlankName() {
    assertEquals(
        Set.of("displayName", "email"),
        invalidFields(new RegistrationRequest(" ", "invalid-email", "Valid-pass-1")));
  }

  @Test
  void registrationEnforcesUtf8ByteLimit() {
    assertEquals(
        Set.of("password"),
        invalidFields(new RegistrationRequest("Student", "student@example.test", "я".repeat(37))));
  }

  @Test
  void adminCreationRequiresRoles() {
    assertEquals(
        Set.of("roles"),
        invalidFields(
            new AdminCreateUserRequest(
                "Student", "student@example.test", "Valid-pass-1", Set.of(), false)));
  }

  @Test
  void questionValidatesNestedOption() {
    var request =
        new CreateQuestionRequest(
            "Question",
            QuestionType.SINGLE_CHOICE,
            1,
            List.of(
                new CreateQuestionRequest.OptionRequest(" ", true),
                new CreateQuestionRequest.OptionRequest("Wrong", false)),
            null);
    assertTrue(
        invalidFields(request).stream()
            .anyMatch(path -> path.contains("options") && path.endsWith("text")));
  }
}
