
package org.courseflow.security;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityExceptionHandler securityExceptionHandler,
            UrlBasedCorsConfigurationSource corsConfigurationSource,
            @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled
    ) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .exceptionHandling(exceptions ->
                        exceptions
                                .authenticationEntryPoint(
                                        securityExceptionHandler
                                )
                                .accessDeniedHandler(
                                        securityExceptionHandler
                                )
                )

                .authorizeHttpRequests(auth -> EndpointAuthorization.configure(auth, apiDocsEnabled))

                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(
                                securityExceptionHandler
                        )
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter()
                                )
                        )
                );

        return http.build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${courseflow.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String origins) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> allowed = Arrays.stream(origins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (allowed.contains("*")) {
            throw new IllegalArgumentException("Configure explicit CORS origins, not '*'");
        }
        configuration.setAllowedOrigins(allowed);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Location", "Retry-After", "X-Request-Id", "WWW-Authenticate"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter rolesConverter =
                new JwtGrantedAuthoritiesConverter();

        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                rolesConverter
        );

        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
