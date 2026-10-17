package br.com.imarui.ima.platform.security;

import br.com.imarui.ima.platform.security.jwt.*;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.MediaType;

/** Resource protection contract only; token issuance/login belongs to Authentication. */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class IdentityApiSecurityConfiguration {
    private final java.time.Clock clock;
    private final tools.jackson.databind.json.JsonMapper json;
    private final JwtSigningKeyProvider keys;
    private final JwtProperties properties;
    private final IdentityRepository identities;
    private final AffiliationRepository affiliations;
    private final br.com.imarui.ima.identity.infra.persistence.jpa.AccessRevocationRequestJpaRepository revocations;

    @Bean
    public JwtDecoder jwtDecoder() {
        var decoder = NimbusJwtDecoder.withSecretKey(keys.getKey()).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(properties.getIssuer()), jwt -> {
            try {
                var id = IdentityId.from(jwt.getSubject());
                if (jwt.getIssuedAt() == null) return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
                boolean active = identities.findById(id).filter(Identity::isActive).isPresent()
                    && !revocations.existsByScopeAndSubjectIdAndRequestedAtGreaterThanEqual("IDENTITY", id.value(), jwt.getIssuedAt());
                var tenant = jwt.getClaimAsString("tenant_id");
                if (tenant != null) {
                    active = active && !revocations.existsByScopeAndSubjectIdAndRequestedAtGreaterThanEqual("TENANT", java.util.UUID.fromString(tenant), jwt.getIssuedAt())
                        && affiliations.findByTenantId(br.com.imarui.ima.identity.core.domain.model.tenant.TenantId.from(tenant))
                        .stream().anyMatch(affiliation -> affiliation.getIdentityId().equals(id));
                }
                if (active) return OAuth2TokenValidatorResult.success();
            } catch (IllegalArgumentException exception) { /* Invalid subject/context is never accepted. */ }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Identity or tenant context is unavailable.", null));
        }));
        return decoder;
    }
    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("permissions"); authorities.setAuthorityPrefix("");
        var converter = new JwtAuthenticationConverter(); converter.setJwtGrantedAuthoritiesConverter(authorities);
        org.springframework.security.web.AuthenticationEntryPoint unauthenticated = (request, response, exception) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            writeProblem(response, 401, "AUTHENTICATION_REQUIRED", "Unauthorized");
        };
        org.springframework.security.web.access.AccessDeniedHandler forbidden = (request, response, exception) ->
            writeProblem(response, 403, "ACCESS_DENIED", "Forbidden");
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/v1/identity-applications").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                .authenticationEntryPoint(unauthenticated).accessDeniedHandler(forbidden))
            .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthenticated).accessDeniedHandler(forbidden))
            .build();
    }
    private void writeProblem(jakarta.servlet.http.HttpServletResponse response, int status, String code, String title) throws java.io.IOException {
        var problem = org.springframework.http.ProblemDetail.forStatus(status);
        problem.setTitle(title); problem.setProperty("code", code); problem.setProperty("timestamp", clock.instant());
        response.setStatus(status); response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }
}
