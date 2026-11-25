package org.courseflow.auth.service;

import java.util.UUID;
import org.courseflow.auth.entity.AuthSession;
import org.courseflow.auth.repository.AuthSessionRepository;
import org.courseflow.common.exception.InvalidSessionException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutService {

  private final AuthSessionRepository authSessionRepository;

  public LogoutService(AuthSessionRepository authSessionRepository) {
    this.authSessionRepository = authSessionRepository;
  }

  @Transactional
  public void logout(Jwt jwt) {
    String id = jwt.getId();
    if (id == null) {
      throw new InvalidSessionException();
    }
    UUID sessionId;
    try {
      sessionId = UUID.fromString(id);
    } catch (IllegalArgumentException exception) {
      throw new InvalidSessionException();
    }

    AuthSession session =
        authSessionRepository.findById(sessionId).orElseThrow(InvalidSessionException::new);

    session.revoke();
  }
}
