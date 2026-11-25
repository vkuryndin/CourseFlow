package org.courseflow.security;

import org.courseflow.auth.api.SessionAccess;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AuthSessionStartupCleanup implements ApplicationRunner {

  private final SessionAccess sessionAccess;

  public AuthSessionStartupCleanup(SessionAccess sessionAccess) {
    this.sessionAccess = sessionAccess;
  }

  @Override
  public void run(ApplicationArguments args) {
    sessionAccess.invalidateAllOnStartup();
  }
}
