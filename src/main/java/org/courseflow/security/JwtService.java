package org.courseflow.security;

import java.util.List;
import org.courseflow.auth.api.AccessTokenIssuer;
import org.courseflow.auth.entity.AuthSession;
import org.courseflow.identity.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService implements AccessTokenIssuer {

  private final JwtEncoder jwtEncoder;

  public JwtService(JwtEncoder jwtEncoder) {
    this.jwtEncoder = jwtEncoder;
  }

  @Override
  public String generateAccessToken(AuthSession session) {
    User user = session.getUserAccount().getUser();

    List<String> roles = user.getRoles().stream().map(Enum::name).sorted().toList();

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(JwtConfig.ISSUER)
            .subject(user.getId().toString())
            .id(session.getId().toString())
            .issuedAt(session.getCreatedAt())
            .expiresAt(session.getExpiresAt())
            .claim("roles", roles)
            .claim("passwordChangeRequired", session.getUserAccount().isPasswordChangeRequired())
            .build();

    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();

    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
