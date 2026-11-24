
package org.courseflow.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

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
            SecurityExceptionHandler securityExceptionHandler,
            UrlBasedCorsConfigurationSource corsConfigurationSource
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

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(HttpMethod.GET, "/api/users/me/profile").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/users/me/profile").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/teachers/*").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/users/me/assignments",
                                "/api/users/me/submissions",
                                "/api/users/me/quiz-attempts").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.PUT, "/api/categories/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/categories/*").hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/dashboard/student"
                        ).hasRole("STUDENT")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/dashboard/teacher"
                        ).hasRole("TEACHER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/dashboard/admin"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/users/me/progress"
                        ).hasRole("STUDENT")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/courses/*/gradebook"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/courses/*/reviews"
                        ).hasRole("STUDENT")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/courses/*/reviews/me"
                        ).hasRole("STUDENT")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/courses/*/reviews/me"
                        ).hasRole("STUDENT")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/courses/*/reviews/me"
                        ).hasRole("STUDENT")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/courses/*/reviews",
                                "/api/courses/*/reviews/rating"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/quizzes/*/attempts",
                                "/api/quizzes/*/attempts/*/manage"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/quizzes/*/attempts/me",
                                "/api/quizzes/*/attempts/*/result"
                        ).hasRole("STUDENT")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/quizzes/*/attempts/*/submit"
                        ).hasRole("STUDENT")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/quizzes/*/attempts"
                        ).hasRole("STUDENT")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/quizzes/*/attempts/*"
                        ).hasRole("STUDENT")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/quizzes/*",
                                "/api/quizzes/*/questions/*"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/quizzes/*",
                                "/api/quizzes/*/questions/*"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/quizzes/*/questions"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/quizzes/*/questions/manage"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/quizzes/*/questions"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/modules/*/quiz"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/modules/*/quiz",
                                "/api/quizzes/*"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        ).permitAll()


                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/users/me"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/users/me/enrollments"
                        ).hasRole("STUDENT")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/users", "/api/users/*"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/users",
                                "/api/users/*/activate",
                                "/api/users/*/deactivate",
                                "/api/users/*/password-reset"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/users/*", "/api/users/*/roles"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/users/*"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/categories",
                                "/api/categories/*"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/categories"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/courses",
                                "/api/courses/*"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/courses"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/courses/*/teacher"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/courses/*"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/courses/*/archive"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/courses/*"
                        ).hasAnyRole("ADMIN", "TEACHER")


                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/tags",
                                "/api/tags/*"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/tags"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/tags/*"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/tags/*"
                        ).hasRole("ADMIN")


                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/courses/*/tags"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/courses/*/tags/*"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/courses/*/tags/*"
                        ).hasAnyRole("ADMIN", "TEACHER")


                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/courses/*/modules"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/courses/*/modules"
                        ).hasAnyRole("ADMIN", "TEACHER")


                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/courses/*/modules/*"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/courses/*/modules/*"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/courses/*/modules/*/position"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/courses/*/modules/*"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/courses/*/modules/*/lessons",
                                "/api/courses/*/modules/*/lessons/*"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/courses/*/modules/*/lessons"
                        ).hasAnyRole("ADMIN", "TEACHER")


                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/courses/*/modules/*/lessons/*"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/courses/*/modules/*/lessons/*/position"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/courses/*/modules/*/lessons/*"
                        ).hasAnyRole("ADMIN", "TEACHER")


                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/lessons/*/sessions",
                                "/api/lessons/*/sessions/*"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/lessons/*/sessions"
                        ).hasAnyRole("ADMIN", "TEACHER")


                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/lessons/*/sessions/*"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/lessons/*/sessions/*/time"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/lessons/*/sessions/*"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/courses/*/enrollments"
                        ).hasRole("STUDENT")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/courses/*/enrollments/me"
                        ).hasRole("STUDENT")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/courses/*/enrollments/me"
                        ).hasRole("STUDENT")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/courses/*/enrollments/students/*"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/courses/*/enrollments"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/lessons/*/assignments"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/lessons/*/assignments/*"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/lessons/*/assignments/*"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/lessons/*/assignments",
                                "/api/lessons/*/assignments/*"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/assignments/*/submissions"
                        ).hasRole("STUDENT")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/assignments/*/submissions/me"
                        ).hasRole("STUDENT")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/assignments/*/submissions"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/assignments/*/submissions/*/grade"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/logout",
                                "/api/auth/change-password"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/system/status"
                        ).authenticated()

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
        configuration.setExposedHeaders(List.of("Location", "Retry-After"));
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
