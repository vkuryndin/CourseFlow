package org.courseflow.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.Filter;
import java.util.Set;
import java.util.UUID;
import org.courseflow.auth.api.AccountCreator;
import org.courseflow.auth.dto.LoginRequest;
import org.courseflow.auth.service.AuthService;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.integration.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Uses the existing remote Docker/Testcontainers integration environment and real JWTs. */
class SystemInfoIT extends AbstractIntegrationTest {
  @Autowired private WebApplicationContext context;

  @Autowired
  @Qualifier("springSecurityFilterChain")
  private Filter springSecurityFilterChain;

  @Autowired private AccountCreator accountCreator;
  @Autowired private AuthService authService;
  @Autowired private Environment environment;

  @Test
  void endpointAllowsOnlyAdminAndDoesNotChangeStatusEndpoint() throws Exception {
    var mvc =
        MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    String admin =
        authService
            .login(
                new LoginRequest(
                    environment.getRequiredProperty("COURSEFLOW_ADMIN_EMAIL"),
                    environment.getRequiredProperty("COURSEFLOW_ADMIN_PASSWORD")))
            .accessToken();
    String student = createToken(UserRole.STUDENT, false);
    String teacher = createToken(UserRole.TEACHER, false);
    String temporary = createToken(UserRole.ADMIN, true);
    mvc.perform(get("/api/system/info")).andExpect(status().isUnauthorized());
    for (String denied : new String[] {student, teacher, temporary}) {
      mvc.perform(get("/api/system/info").header(HttpHeaders.AUTHORIZATION, "Bearer " + denied))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get("/api/system/info").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(jsonPath("$.snapshot.database.status").value("CONNECTED"))
        .andExpect(jsonPath("$.snapshot.database.databaseName").value("courseflow_test"))
        .andExpect(jsonPath("$.snapshot.database.tableCount").value(22))
        .andExpect(jsonPath("$.snapshot.database.serverPort").value(5432))
        .andExpect(jsonPath("$.snapshot.runtime.javaVersion").isString())
        .andExpect(jsonPath("$.accessTokenLifetimeSeconds").value(3600))
        .andExpect(jsonPath("$.snapshot.mail.status").value("NOT_CHECKED"))
        .andExpect(jsonPath("$.snapshot.database.password").doesNotExist())
        .andExpect(jsonPath("$.snapshot.database.url").doesNotExist())
        .andExpect(jsonPath("$.snapshot.mail.password").doesNotExist());
    mvc.perform(get("/api/system/status").header(HttpHeaders.AUTHORIZATION, "Bearer " + student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.application").value("CourseFlow"))
        .andExpect(jsonPath("$.database").value("CONNECTED"));
  }

  private String createToken(UserRole role, boolean temporaryPassword) {
    String email = "diagnostics-" + UUID.randomUUID() + "@example.invalid";
    String password = "Diagnostics-Test-Password-1!";
    accountCreator.create(
        "Diagnostics test", email, Set.of(role), password, temporaryPassword, user -> user.getId());
    return authService.login(new LoginRequest(email, password)).accessToken();
  }
}
