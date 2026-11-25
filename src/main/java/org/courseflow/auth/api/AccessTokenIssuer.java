package org.courseflow.auth.api;

import org.courseflow.auth.entity.AuthSession;

/** Issues an access token for an established authentication session. */
public interface AccessTokenIssuer {
  String generateAccessToken(AuthSession session);
}
