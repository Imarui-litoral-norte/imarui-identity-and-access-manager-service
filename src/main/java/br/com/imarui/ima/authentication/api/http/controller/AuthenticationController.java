package br.com.imarui.ima.authentication.api.http.controller;

import br.com.imarui.ima.authentication.core.application.usecase.AuthenticationUseCases;
import br.com.imarui.ima.authentication.core.domain.model.AuthenticationChallenge.Purpose;
import br.com.imarui.ima.authentication.infra.security.AuthenticationRateLimiter;
import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/authentication")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(
    name = "Authentication",
    description = "Primeiro acesso, credenciais, sessões e tokens web")
public class AuthenticationController {
  private final AuthenticationUseCases auth;
  private final AuthenticationRateLimiter limits;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Value("${security.auth.cookie-same-site:Lax}")
  private String sameSite;

  @org.springframework.beans.factory.annotation.Value("${security.auth.require-https:true}")
  private boolean requireHttps;

  public record Login(
      IdentityKind identityKind,
      @NotBlank @Size(max = 254) String identifier,
      @NotBlank @Size(max = 72) String password) {
    @Override
    public String toString() {
      return "Login[REDACTED]";
    }
  }

  public record Identification(
      IdentityKind identityKind, @NotBlank @Size(max = 254) String identifier) {}

  public record Complete(
      @NotBlank @Size(max = 256) String token, @NotBlank @Size(max = 72) String password) {
    @Override
    public String toString() {
      return "Complete[REDACTED]";
    }
  }

  public record Change(
      @NotBlank @Size(max = 72) String currentPassword,
      @NotBlank @Size(max = 72) String newPassword) {
    @Override
    public String toString() {
      return "Change[REDACTED]";
    }
  }

  public record Access(String accessToken, String tokenType, long expiresIn) {
    @Override
    public String toString() {
      return "Access[REDACTED]";
    }
  }

  @GetMapping("/csrf")
  @io.swagger.v3.oas.annotations.Operation(
      summary = "Obter token anti-CSRF",
      description =
          "Frontend usa credentials:include e envia token em X-XSRF-TOKEN ao realizar login,"
              + " refresh ou logout.")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @PostMapping("/login")
  @io.swagger.v3.oas.annotations.Operation(
      summary = "Autenticar Identity",
      description =
          "Kind explícito; PERSON nickname/CPF, LEGAL_ENTITY nickname/CNPJ, SERVICE nickname."
              + " Apelido, CPF, CNPJ ou e-mail resolvem uma única Identity sem informar o tipo. Access JWT 900s; refresh somente em cookie HttpOnly Secure."
              + " Requer X-XSRF-TOKEN.")
  public ResponseEntity<Access> login(@Valid @RequestBody Login body, HttpServletRequest request) {
    secure(request);
    throttle("login", normalize(body.identifier()), request, 30);
    return response(auth.login(body.identityKind(), body.identifier(), body.password()));
  }

  @PostMapping("/refresh")
  @io.swagger.v3.oas.annotations.Operation(
      summary = "Rotacionar refresh cookie",
      description =
          "Consumo único; replay revoga sessão. Requer X-XSRF-TOKEN. Limite absoluto de sete dias"
              + " desde login.")
  public ResponseEntity<Access> refresh(
      @CookieValue(name = "IIAM_REFRESH", required = false) String token,
      HttpServletRequest request) {
    secure(request);
    throttle("refresh", request.getRemoteAddr(), request, 60);
    return response(auth.refresh(token));
  }

  @PostMapping("/logout")
  @io.swagger.v3.oas.annotations.Operation(
      summary = "Encerrar sessão",
      description =
          "Frontend chama IIAM diretamente. Revoga sessão e refresh, limpa cookie; idempotente. JWT"
              + " em consumidores offline pode sobreviver até exp (15 min).")
  public ResponseEntity<Void> logout(
      @CookieValue(name = "IIAM_REFRESH", required = false) String token,
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest request) {
    secure(request);
    auth.logout(
        jwt == null ? null : UUID.fromString(jwt.getSubject()),
        jwt == null ? null : UUID.fromString(jwt.getClaimAsString("sid")),
        token);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO))
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .build();
  }

  @PostMapping("/activation/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @io.swagger.v3.oas.annotations.Operation(
      summary = "Solicitar primeiro acesso",
      description =
          "Resposta genérica. Envia convite ao contato configurado. SERVICE também possui e-mail e"
              + " recebe convite por operador autorizado.")
  public void activationRequest(@Valid @RequestBody Identification body, HttpServletRequest req) {
    secure(req);
    throttle("activation", normalize(body.identifier()), req, 5);
    auth.request(body.identityKind(), body.identifier(), Purpose.ACCOUNT_ACTIVATION);
  }

  @PostMapping("/recovery/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @io.swagger.v3.oas.annotations.Operation(
      summary = "Solicitar recuperação",
      description =
          "Resposta genérica independentemente da existência da conta. E-mail é somente canal de"
              + " entrega.")
  public void recoveryRequest(@Valid @RequestBody Identification body, HttpServletRequest req) {
    secure(req);
    throttle("recovery", normalize(body.identifier()), req, 5);
    auth.request(body.identityKind(), body.identifier(), Purpose.PASSWORD_RESET);
  }

  @PostMapping("/activation/complete")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @io.swagger.v3.oas.annotations.Operation(
      summary = "Concluir primeiro acesso",
      description =
          "Convite secreto de uso único e senha. Credential e PENDING→ACTIVE confirmam na mesma"
              + " transação. Inclui SERVICE externo/local; depois chamar login.")
  public void activate(@Valid @RequestBody Complete body, HttpServletRequest req) {
    secure(req);
    throttle("challenge", req.getRemoteAddr(), req, 30);
    auth.complete(body.token(), body.password(), Purpose.ACCOUNT_ACTIVATION);
  }

  @PostMapping("/recovery/complete")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @io.swagger.v3.oas.annotations.Operation(
      summary = "Concluir recuperação",
      description = "Consome challenge de recuperação; revoga sessões e tokens antigos.")
  public void reset(@Valid @RequestBody Complete body, HttpServletRequest req) {
    secure(req);
    throttle("challenge", req.getRemoteAddr(), req, 30);
    auth.complete(body.token(), body.password(), Purpose.PASSWORD_RESET);
  }

  @PostMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @io.swagger.v3.oas.annotations.Operation(
      summary = "Trocar senha",
      description =
          "Exige Bearer válido e senha atual; revoga todas as sessões, inclusive a atual.")
  public void change(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Change body) {
    auth.changePassword(
        UUID.fromString(jwt.getSubject()), body.currentPassword(), body.newPassword());
  }

  @GetMapping("/sessions")
  @io.swagger.v3.oas.annotations.Operation(
      summary = "Listar próprias sessões",
      description =
          "Até 100 sessões da Identity do JWT validado. Não aceita proprietário no payload.")
  public List<AuthenticationUseCases.OwnSession> sessions(@AuthenticationPrincipal Jwt jwt) {
    return auth.sessions(UUID.fromString(jwt.getSubject()));
  }

  @DeleteMapping("/sessions/{sid}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @io.swagger.v3.oas.annotations.Operation(
      summary = "Encerrar própria sessão",
      description = "SID precisa pertencer à Identity autenticada. Revoga refresh relacionados.")
  public void end(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sid) {
    auth.endSession(UUID.fromString(jwt.getSubject()), sid);
  }

  @PostMapping("/admin/identities/{id}/invitation")
  @PreAuthorize("hasAuthority('AUTHENTICATION_INVITATION_CREATE')")
  @br.com.imarui.ima.platform.openapi.security.RequiredPermission(
      "AUTHENTICATION_INVITATION_CREATE")
  @io.swagger.v3.oas.annotations.Operation(
      summary = "Provisionar convite de primeiro acesso",
      description =
          "Operador autorizado recebe segredo uma única vez para entrega segura a SERVICE ou"
              + " titular. Não é endpoint público de emissão.")
  public ResponseEntity<Map<String, String>> invitation(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest req) {
    secure(req);
    throttle("admin-invitation", jwt.getSubject(), req, 5);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            Map.of(
                "invitation",
                auth.administrativeInvitation(
                    UUID.fromString(jwt.getSubject()), id, req.getRemoteAddr())));
  }

  private ResponseEntity<Access> response(AuthenticationUseCases.Issued result) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.PRAGMA, "no-cache")
        .header(
            HttpHeaders.SET_COOKIE,
            cookie(
                result.refreshToken(),
                Duration.between(clock.instant(), result.sessionExpiresAt())))
        .body(new Access(result.accessToken(), "Bearer", 900));
  }

  private String cookie(String value, Duration ttl) {
    if (!Set.of("Lax", "Strict", "None").contains(sameSite))
      throw new IllegalStateException("Invalid SameSite policy.");
    return ResponseCookie.from("IIAM_REFRESH", value)
        .httpOnly(true)
        .secure(true)
        .sameSite(sameSite)
        .path("/authentication")
        .maxAge(ttl.isNegative() ? Duration.ZERO : ttl)
        .build()
        .toString();
  }

  private void secure(HttpServletRequest req) {
    if (requireHttps && !req.isSecure()) throw new IllegalArgumentException("HTTPS is required.");
  }

  private String normalize(String value) {
    String s = value.trim().toLowerCase(Locale.ROOT);
    return s.matches("[0-9. /-]+") ? s.replaceAll("[^0-9]", "") : s;
  }

  private void throttle(String op, String account, HttpServletRequest req, int max) {
    limits.check(op + "-origin", req.getRemoteAddr(), max);
    limits.check(op + "-account", account, max);
  }
}
