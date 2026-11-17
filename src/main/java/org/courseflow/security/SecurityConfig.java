
package org.courseflow.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityExceptionHandler securityExceptionHandler
    ) throws Exception {

        http
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

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/api/system/status",
                                "/api/categories",
                                "/api/categories/**"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/users/me"
                        ).authenticated()

                        .requestMatchers(
                                "/api/users",
                                "/api/users/**"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/categories"
                        ).hasRole("ADMIN")

                        .requestMatchers("/api/**").authenticated()

                        .anyRequest().denyAll()
                )

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
