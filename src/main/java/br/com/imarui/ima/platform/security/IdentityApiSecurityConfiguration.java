package br.com.imarui.ima.platform.security;

import br.com.imarui.ima.authentication.core.port.AuthenticationStore;
import br.com.imarui.ima.authorization.api.module.IdentityAuthorities;
import br.com.imarui.ima.identity.api.module.IdentityAuthenticationApi;
import br.com.imarui.ima.platform.security.jwt.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.source.*;
import com.nimbusds.jose.proc.*;
import com.nimbusds.jwt.proc.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class IdentityApiSecurityConfiguration {
  private final Clock clock;
  private final tools.jackson.databind.json.JsonMapper json;
  private final JwtSigningKeyProvider keys;
  private final JwtProperties properties;
  private final IdentityAuthenticationApi identities;
  private final AuthenticationStore store;
  private final IdentityAuthorities authorities;
  private final br.com.imarui.ima.authentication.infra.security.AuthenticationRateLimiter authorizationLimits;
  private final br.com.imarui.ima.authorization.api.module.SecurityAuditEvents authorizationEvents;

  @org.springframework.beans.factory.annotation.Value("${security.auth.allowed-origins:}")
  private String allowedOrigins;

  @Bean
  public JwtDecoder jwtDecoder() {
    var processor = new DefaultJWTProcessor<com.nimbusds.jose.proc.SecurityContext>();
    processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType("at+jwt")));
    processor.setJWSKeySelector(
        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, new ImmutableJWKSet<>(keys.jwks())));
    var decoder = new NimbusJwtDecoder(processor);
    var timestamp = new JwtTimestampValidator(Duration.ZERO);
    timestamp.setClock(clock);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            timestamp,
            new JwtIssuerValidator(properties.getIssuer()),
            jwt -> {
              try {
                if (!jwt.getAudience().contains(properties.getAudience())
                    || jwt.getIssuedAt() == null
                    || jwt.getExpiresAt() == null
                    || jwt.getId() == null
                    || jwt.getIssuedAt().isAfter(clock.instant())
                    || Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds() > 900)
                  return invalid();
                var id = UUID.fromString(jwt.getSubject());
                var sid = UUID.fromString(jwt.getClaimAsString("sid"));
                var session = store.session(sid, false).orElse(null);
                if (session == null
                    || !id.equals(session.getIdentityId())
                    || !session.isValid(clock.instant())
                    || jwt.getIssuedAt()
                        .isBefore(
                            session
                                .getCreatedAt()
                                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)))
                  return invalid();
                return identities.valid(
                        id, session.getCreatedAt(), jwt.getClaimAsString("tenant_id"))
                    ? OAuth2TokenValidatorResult.success()
                    : invalid();
              } catch (IllegalArgumentException e) {
                return invalid();
              }
            }));
    return decoder;
  }

  private OAuth2TokenValidatorResult invalid() {
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    var config = new CorsConfiguration();
    var origins =
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();
    if (origins.stream().anyMatch(s -> s.contains("*")))
      throw new IllegalStateException("Explicit CORS origins required.");
    config.setAllowedOrigins(origins);
    config.setAllowCredentials(true);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-XSRF-TOKEN"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
    var administrativeRequests=new org.springframework.web.filter.OncePerRequestFilter() {
      protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res,
          jakarta.servlet.FilterChain chain) throws jakarta.servlet.ServletException,java.io.IOException {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean administrative=req.getRequestURI().startsWith("/api/v1/authorization/admin/")
            || req.getRequestURI().startsWith("/api/v1/admin/");
        if(!administrative || !(auth instanceof JwtAuthenticationToken jwt)) { chain.doFilter(req,res); return; }
        try {
          authorizationLimits.check("authorization-admin",jwt.getName(),900);
          chain.doFilter(req,res);
        } catch(br.com.imarui.ima.authentication.infra.security.AuthenticationRateLimiter.Limited limited) {
          writeProblem(res,429,"RATE_LIMITED","Too Many Requests");
        } finally {
          if(Set.of(401,403,409,429).contains(res.getStatus()))
            authorizationEvents.record("ADMINISTRATIVE_REJECTED",UUID.fromString(jwt.getName()),"iiam",null,null,
                "HTTP_"+res.getStatus(),UUID.randomUUID());
        }
      }
    };
    org.springframework.security.web.AuthenticationEntryPoint unauthenticated =
        (req, res, ex) -> {
          res.setHeader("WWW-Authenticate", "Bearer");
          writeProblem(res, 401, "AUTHENTICATION_REQUIRED", "Unauthorized");
        };
    org.springframework.security.web.access.AccessDeniedHandler forbidden =
        (req, res, ex) -> writeProblem(res, 403, "ACCESS_DENIED", "Forbidden");
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt ->
            authorities.find(UUID.fromString(jwt.getSubject())).stream()
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .map(a -> (org.springframework.security.core.GrantedAuthority) a)
                .toList());
    var csrf = new CookieCsrfTokenRepository();
    csrf.setCookiePath("/authentication");
    csrf.setCookieCustomizer(c -> c.secure(true).httpOnly(true).sameSite("Lax"));
    var handler = new CsrfTokenRequestAttributeHandler();
    RequestMatcher browserMutation =
        req ->
            !Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(req.getMethod())
                && Set.of(
                        "/authentication/login",
                        "/authentication/refresh",
                        "/authentication/logout")
                    .contains(req.getRequestURI());
    return http.cors(org.springframework.security.config.Customizer.withDefaults())
        .csrf(
            c ->
                c.csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(handler)
                    .requireCsrfProtectionMatcher(browserMutation))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterAfter(administrativeRequests,org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/identity-applications",
                        "/api/v1/identity-applications/users",
                        "/api/v1/identity-applications/services",
                        "/authentication/login",
                        "/authentication/refresh",
                        "/authentication/logout",
                        "/authentication/activation/request",
                        "/authentication/activation/complete",
                        "/authentication/recovery/request",
                        "/authentication/recovery/complete")
                    .permitAll()
                    .requestMatchers(
                        "/authentication/csrf",
                        "/.well-known/jwks.json",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/docs",
                        "/docs/",
                        "/openapi-custom/**",
                        "/actuator/health")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .hasAuthority("AUTHORIZATION_AUDIT_READ")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            r ->
                r.jwt(j -> j.jwtAuthenticationConverter(converter))
                    .authenticationEntryPoint(unauthenticated)
                    .accessDeniedHandler(forbidden))
        .exceptionHandling(
            e -> e.authenticationEntryPoint(unauthenticated).accessDeniedHandler(forbidden))
        .headers(
            h ->
                h.referrerPolicy(
                    r ->
                        r.policy(
                            org.springframework.security.web.header.writers
                                .ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
        .build();
  }

  private void writeProblem(
      jakarta.servlet.http.HttpServletResponse res, int status, String code, String title)
      throws java.io.IOException {
    var p = ProblemDetail.forStatus(status);
    p.setTitle(title);
    p.setProperty("code", code);
    p.setProperty("timestamp", clock.instant());
    res.setStatus(status);
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(res.getOutputStream(), p);
  }
}
