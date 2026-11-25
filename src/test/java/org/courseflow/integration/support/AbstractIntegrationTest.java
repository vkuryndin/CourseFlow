package org.courseflow.integration.support;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "springdoc.api-docs.enabled=true",
      "springdoc.swagger-ui.enabled=true",
      "springdoc.paths-to-match=/api/**",
      "springdoc.override-with-generic-response=false",
      "server.address=127.0.0.1"
    })
@ActiveProfiles("test")
@Tag("integration")
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {

  private static final DockerSshTunnel SSH_TUNNEL;
  private static final PostgreSQLContainer POSTGRES;

  static {
    try {
      SSH_TUNNEL = DockerSshTunnel.open();
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to initialize Docker SSH tunnel", exception);
    }

    try {
      POSTGRES =
          new PostgreSQLContainer("postgres:18")
              .withDatabaseName("courseflow_test")
              .withUsername("test")
              .withPassword("test");
      POSTGRES.start();

      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    try {
                      POSTGRES.stop();
                    } finally {
                      SSH_TUNNEL.close();
                    }
                  },
                  "docker-ssh-shutdown"));
    } catch (RuntimeException exception) {
      SSH_TUNNEL.close();
      throw exception;
    }
  }

  @DynamicPropertySource
  static void configureDatabase(DynamicPropertyRegistry registry) {
    // Register the actual datasource keys as well as the existing test YAML placeholders.
    // This prevents the main application configuration from selecting a different database.
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    registry.add("courseflow.test.jdbc-url", POSTGRES::getJdbcUrl);
    registry.add("courseflow.test.db-username", POSTGRES::getUsername);
    registry.add("courseflow.test.db-password", POSTGRES::getPassword);
  }
}
