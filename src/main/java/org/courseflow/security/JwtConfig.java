
package org.courseflow.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    public static final String ISSUER = "courseflow";

    @Bean
    public SecretKey jwtSigningKey(
            @Value("${JWT_SECRET_BASE64}") String encodedKey
    ) {
        byte[] keyBytes = Base64.getDecoder().decode(encodedKey);

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT signing key must be at least 256 bits"
            );
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return NimbusJwtEncoder
                .withSecretKey(jwtSigningKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSigningKey,
            JwtSessionValidator sessionValidator
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<Jwt>(
                        JwtValidators.createDefaultWithIssuer(ISSUER),
                        sessionValidator
                )
        );

        return decoder;
    }
}
