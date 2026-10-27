package com.genisource.erp.platform.security;

import com.genisource.erp.platform.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stateless JWT security.
 *
 * <p>Two details matter for multi-tenancy:
 * <ol>
 *   <li>the tenant is pushed into {@link TenantContext} while the token is converted,
 *       i.e. before any repository call in the request;</li>
 *   <li>{@link TenantCleanupFilter} clears it afterwards, because servlet threads are
 *       pooled and a stale tenant would leak into the next request.</li>
 * </ol>
 */
@EnableWebSecurity
@EnableMethodSecurity
@org.springframework.context.annotation.Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/login",
            "/api/auth/refresh",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @org.springframework.context.annotation.Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @org.springframework.context.annotation.Bean
    JwtDecoder jwtDecoder(SecurityProperties properties) {
        SecretKeySpec key = new SecretKeySpec(
                properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @org.springframework.context.annotation.Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ObjectMapper objectMapper,
                                            SecurityProperties properties) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((request, response, ex) ->
                                writeProblem(objectMapper, response, HttpStatus.UNAUTHORIZED,
                                        "unauthorized", "Authentication is required")))
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, ex) ->
                                writeProblem(objectMapper, response, HttpStatus.FORBIDDEN,
                                        "forbidden", "You do not have permission to perform this action")));
        return http.build();
    }

    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName(JwtService.CLAIM_USERNAME);
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Number tenantId = jwt.getClaim(JwtService.CLAIM_TENANT);
            if (tenantId != null) {
                TenantContext.set(tenantId.longValue());
            }

            List<GrantedAuthority> authorities = new ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList(JwtService.CLAIM_ROLES);
            if (roles != null) {
                roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            }
            List<String> permissions = jwt.getClaimAsStringList(JwtService.CLAIM_PERMISSIONS);
            if (permissions != null) {
                permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
            }
            return authorities;
        });
        return converter;
    }

    private static void writeProblem(ObjectMapper objectMapper,
                                     HttpServletResponse response,
                                     HttpStatus status,
                                     String code,
                                     String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "type", "urn:genisource:erp:error:" + code,
                "title", status.getReasonPhrase(),
                "status", status.value(),
                "detail", detail,
                "code", code));
    }

    /**
     * Clears the thread-local tenant after every request. Ordered before the security
     * chain so that it is the outermost filter of the application.
     */
    @Component
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public static class TenantCleanupFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
