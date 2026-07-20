package com.realis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realis.dto.ErrorResponse;
import com.realis.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
        HttpSecurity http,
        JwtAuthFilter jwtAuthFilter,
        ObjectMapper objectMapper,
        CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .headers(headers -> headers
                .contentSecurityPolicy(csp ->
                    csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'"))
                .referrerPolicy(ref ->
                    ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .frameOptions(frame -> frame.deny())
            )
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(jsonAuthenticationEntryPoint(objectMapper))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/health",
                    "/actuator/health",
                    "/api/auth/**",
                    "/api/verify/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Sans httpBasic()/formLogin() ni entry point explicite, Spring Security retombe sur
     * Http403ForbiddenEntryPoint (403) pour toute requête non authentifiée, alors que le
     * frontend distingue "session expirée" (401) de "accès interdit" (403). On force donc
     * un 401 JSON, cohérent avec le format ErrorResponse utilisé par GlobalExceptionHandler.
     */
    private AuthenticationEntryPoint jsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponse body = ErrorResponse.of(401, "Unauthorized",
                "Authentification requise ou jeton invalide.");
            objectMapper.writeValue(response.getWriter(), body);
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Dérive les origines CORS autorisées de realis.app.frontend-url (FRONTEND_URL) plutôt que
     * d'une liste figée : sinon, tout déploiement avec un FRONTEND_URL en dehors des domaines
     * codés en dur (autre client, staging sur un domaine distinct...) casse silencieusement
     * les appels authentifiés du frontend, alors même que FRONTEND_URL est correctement pris
     * en compte ailleurs (liens absolus dans les certificats PDF, voir PdfCertificateService).
     *
     * "http://localhost:3000" reste toujours autorisé pour permettre le développement local
     * même quand FRONTEND_URL pointe déjà vers un domaine de production.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties appProperties) {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = new ArrayList<>();
        origins.add("http://localhost:3000");

        String frontendUrl = appProperties.frontendUrl();
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            origins.add(frontendUrl);

            // Autorise aussi les sous-domaines du domaine configuré (ex. staging.realis.app
            // si FRONTEND_URL=https://realis.app) : le "*" de Spring exige un caractère avant
            // le point, donc le domaine racine doit rester listé explicitement ci-dessus.
            URI uri = URI.create(frontendUrl);
            if (uri.getHost() != null && uri.getScheme() != null) {
                origins.add(uri.getScheme() + "://*." + uri.getHost());
            }
        }

        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
