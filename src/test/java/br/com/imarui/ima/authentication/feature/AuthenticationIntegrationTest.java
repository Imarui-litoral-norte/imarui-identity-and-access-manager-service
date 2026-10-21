package br.com.imarui.ima.authentication.feature;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.imarui.ima.authentication.core.application.exception.AuthenticationFailure;
import br.com.imarui.ima.authentication.core.domain.model.AuthenticationChallenge.Purpose;
import br.com.imarui.ima.authentication.core.port.ChallengeMail;
import br.com.imarui.ima.authentication.infra.integration.RevocationProcessor;
import br.com.imarui.ima.authentication.infra.mail.ChallengeDeliveryWorker;
import br.com.imarui.ima.identity.core.application.command.identity.*;
import br.com.imarui.ima.identity.core.application.command.tenant.*;
import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import br.com.imarui.ima.platform.bootstrap.AuthenticationBootstrap;
import io.jsonwebtoken.Jwts;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class AuthenticationIntegrationTest extends IdentityIntegrationSupport {
  @Autowired RevocationProcessor processor;
  @Autowired ChallengeDeliveryWorker delivery;
  @MockitoBean ChallengeMail mail;
  static final String PASSWORD = "Strong initial password 123!";

  private Identity pending(IdentityKind kind, String nickname) {
    var i = sample(kind, kind.name().toLowerCase() + "@example.test");
    i.assignNickname(nickname);
    return identities.save(i);
  }

  private void complete(Identity identity) {
    String raw =
        authentication.issueChallenge(identity.getId().value(), Purpose.ACCOUNT_ACTIVATION);
    authentication.complete(raw, PASSWORD, Purpose.ACCOUNT_ACTIVATION);
  }

  @ParameterizedTest
  @EnumSource(IdentityKind.class)
  void allKindsFirstAccessLoginJwtRotationAndLogout(IdentityKind kind) throws Exception {
    var i = pending(kind, "common-nickname");
    assertThrows(
        AuthenticationFailure.class, () -> authentication.login(kind, "common-nickname", PASSWORD));
    String invite = authentication.issueChallenge(i.getId().value(), Purpose.ACCOUNT_ACTIVATION);
    assertThrows(
        AuthenticationFailure.class,
        () -> authentication.complete(invite, PASSWORD, Purpose.PASSWORD_RESET));
    authentication.complete(invite, PASSWORD, Purpose.ACCOUNT_ACTIVATION);
    assertTrue(identities.findById(i.getId()).orElseThrow().isActive());
    assertThrows(
        AuthenticationFailure.class,
        () -> authentication.complete(invite, PASSWORD, Purpose.ACCOUNT_ACTIVATION));
    var issued = authentication.login(kind, "COMMON-NICKNAME", PASSWORD);
    var jwt =
        Jwts.parser()
            .verifyWith(keys.publicKeys().get(keys.kid()))
            .build()
            .parseSignedClaims(issued.accessToken())
            .getPayload();
    assertEquals(i.getId().toString(), jwt.getSubject());
    assertEquals(
        900,
        Duration.between(jwt.getIssuedAt().toInstant(), jwt.getExpiration().toInstant())
            .getSeconds());
    assertTrue(jwt.getAudience().contains("imarui-iam"));
    var s =
        authenticationStore
            .session(UUID.fromString(jwt.get("sid", String.class)), false)
            .orElseThrow();
    assertEquals(Duration.ofDays(7), Duration.between(s.getCreatedAt(), s.getExpiresAt()));
    assertEquals(
        200,
        http("GET", "/api/v1/identities/" + i.getId(), null, issued.accessToken()).statusCode());
    assertThrows(
        AuthenticationFailure.class,
        () -> authentication.login(kind, kind.name().toLowerCase() + "@example.test", PASSWORD));
    String document =
        kind == IdentityKind.PERSON
            ? "529.982.247-25"
            : kind == IdentityKind.LEGAL_ENTITY ? "11.222.333/0001-81" : null;
    if (document != null) assertNotNull(authentication.login(kind, document, PASSWORD));
    var rotated = authentication.refresh(issued.refreshToken());
    assertEquals(issued.sessionExpiresAt(), rotated.sessionExpiresAt());
    assertNotEquals(issued.refreshToken(), rotated.refreshToken());
    authentication.logout(rotated.refreshToken());
    authentication.logout(rotated.refreshToken());
    assertThrows(AuthenticationFailure.class, () -> authentication.refresh(rotated.refreshToken()));
    assertEquals(
        401,
        http("GET", "/api/v1/identities/" + i.getId(), null, rotated.accessToken()).statusCode());
  }

  @Test
  void nicknameIsUniqueWithinKindAndMayRepeatAcrossKinds() {
    for (var kind : IdentityKind.values()) pending(kind, "same-kind-safe");
    var duplicate = service("different@example.test");
    duplicate.assignNickname("SAME-KIND-SAFE");
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> identities.save(duplicate));
    assertTrue(
        jdbc.queryForObject(
                "select count(*) from ima_identities where nickname='same-kind-safe'",
                Integer.class)
            == 3);
  }

  @Test
  void concurrentNicknameWritesHaveOneWinner() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    try {
      List<Future<Boolean>> futures = new ArrayList<>();
      for (int n = 0; n < 2; n++) {
        final int index = n;
        futures.add(
            pool.submit(
                () -> {
                  gate.await();
                  try {
                    var i = service("race" + index + "@example.test");
                    i.assignNickname("racing-name");
                    identities.save(i);
                    return true;
                  } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    return false;
                  }
                }));
      }
      gate.countDown();
      int successes = 0;
      for (var f : futures) if (f.get(15, TimeUnit.SECONDS)) successes++;
      assertEquals(1, successes);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void parallelRefreshProducesOneSuccessAndRevokesReplay() throws Exception {
    var i = pending(IdentityKind.SERVICE, "race-service");
    complete(i);
    var issued = authentication.login(IdentityKind.SERVICE, "race-service", PASSWORD);
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    try {
      List<Future<Boolean>> futures = new ArrayList<>();
      for (int n = 0; n < 2; n++)
        futures.add(
            pool.submit(
                () -> {
                  gate.await();
                  try {
                    authentication.refresh(issued.refreshToken());
                    return true;
                  } catch (AuthenticationFailure e) {
                    return false;
                  }
                }));
      gate.countDown();
      int successes = 0;
      for (var f : futures) if (f.get(20, TimeUnit.SECONDS)) successes++;
      assertEquals(1, successes);
      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from authentication_refresh_tokens t join authentication_sessions s"
                  + " on s.id=t.session_id where s.identity_id=? and t.status='ACTIVE'",
              Integer.class,
              i.getId().value()));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void parallelChallengeConsumptionIsAtomic() throws Exception {
    var i = pending(IdentityKind.SERVICE, "challenge-race");
    String invite = authentication.issueChallenge(i.getId().value(), Purpose.ACCOUNT_ACTIVATION);
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    try {
      var work =
          (Callable<Boolean>)
              () -> {
                gate.await();
                try {
                  authentication.complete(invite, PASSWORD, Purpose.ACCOUNT_ACTIVATION);
                  return true;
                } catch (AuthenticationFailure e) {
                  return false;
                }
              };
      var a = pool.submit(work);
      var c = pool.submit(work);
      gate.countDown();
      assertEquals(
          1, (a.get(20, TimeUnit.SECONDS) ? 1 : 0) + (c.get(20, TimeUnit.SECONDS) ? 1 : 0));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void expiredChallengeAndWeakPasswordLeaveIdentityPending() {
    var i = pending(IdentityKind.SERVICE, "expired-invite");
    var invite = authentication.issueChallenge(i.getId().value(), Purpose.ACCOUNT_ACTIVATION);
    assertThrows(
        IllegalArgumentException.class,
        () -> authentication.complete(invite, "short", Purpose.ACCOUNT_ACTIVATION));
    jdbc.update(
        "update authentication_challenges set created_at=current_timestamp-interval '2"
            + " days',expires_at=current_timestamp-interval '1 day' where identity_id=?",
        i.getId().value());
    assertThrows(
        AuthenticationFailure.class,
        () -> authentication.complete(invite, PASSWORD, Purpose.ACCOUNT_ACTIVATION));
    assertTrue(identities.findById(i.getId()).orElseThrow().isPending());
  }

  @Test
  void resetRevokesExistingSessionsAndCannotBeReused() {
    var i = pending(IdentityKind.SERVICE, "reset-service");
    complete(i);
    var login = authentication.login(IdentityKind.SERVICE, "reset-service", PASSWORD);
    var reset = authentication.issueChallenge(i.getId().value(), Purpose.PASSWORD_RESET);
    authentication.complete(reset, "New strong password 456!", Purpose.PASSWORD_RESET);
    assertThrows(AuthenticationFailure.class, () -> authentication.refresh(login.refreshToken()));
    assertThrows(
        AuthenticationFailure.class,
        () -> authentication.complete(reset, PASSWORD, Purpose.PASSWORD_RESET));
    assertNotNull(
        authentication.login(IdentityKind.SERVICE, "reset-service", "New strong password 456!"));
  }

  @Test
  void credentialFailuresPersistAndBlockAtFive() {
    var i = pending(IdentityKind.SERVICE, "blocked-service");
    complete(i);
    for (int n = 0; n < 5; n++)
      assertThrows(
          AuthenticationFailure.class,
          () -> authentication.login(IdentityKind.SERVICE, "blocked-service", "wrong"));
    assertEquals(
        5,
        jdbc.queryForObject(
            "select failures from authentication_credentials where identity_id=?",
            Integer.class,
            i.getId().value()));
    assertThrows(
        AuthenticationFailure.class,
        () -> authentication.login(IdentityKind.SERVICE, "blocked-service", PASSWORD));
  }

  @Test
  void absoluteSessionExpiryCannotBeExtended() {
    var i = pending(IdentityKind.SERVICE, "expired-session");
    complete(i);
    var login = authentication.login(IdentityKind.SERVICE, "expired-session", PASSWORD);
    jdbc.update(
        "update authentication_sessions set created_at=current_timestamp-interval '8"
            + " days',expires_at=current_timestamp-interval '1 day' where identity_id=?",
        i.getId().value());
    assertThrows(AuthenticationFailure.class, () -> authentication.refresh(login.refreshToken()));
  }

  @Test
  void identityDisableOutboxAndReactivationDoNotResurrectTokens() {
    var i = pending(IdentityKind.SERVICE, "outbox-service");
    complete(i);
    var login = authentication.login(IdentityKind.SERVICE, "outbox-service", PASSWORD);
    disable.execute(new DisableIdentityCommand(i.getId()));
    assertThrows(AuthenticationFailure.class, () -> authentication.refresh(login.refreshToken()));
    assertTrue(processor.process() > 0);
    assertEquals(0, processor.process());
    reactivate.execute(new ReactivateIdentityCommand(i.getId()));
    assertThrows(AuthenticationFailure.class, () -> authentication.refresh(login.refreshToken()));
    assertNotNull(authentication.login(IdentityKind.SERVICE, "outbox-service", PASSWORD));
  }

  @Test
  void foreignSessionCannotBeClosed() {
    var a = pending(IdentityKind.SERVICE, "owner-service");
    complete(a);
    var issued = authentication.login(IdentityKind.SERVICE, "owner-service", PASSWORD);
    var sid =
        UUID.fromString(
            Jwts.parser()
                .verifyWith(keys.publicKeys().get(keys.kid()))
                .build()
                .parseSignedClaims(issued.accessToken())
                .getPayload()
                .get("sid", String.class));
    assertThrows(
        AuthenticationFailure.class,
        () -> authentication.endSession(reviewer.getId().value(), sid));
    assertNotNull(authentication.refresh(issued.refreshToken()));
  }

  @Test
  void serviceWithoutContactCanUseOperatorInvite() {
    var i =
        br.com.imarui.ima.identity.core.domain.model.identity.service.ServiceIdentity.create(
            IdentityId.generate(),
            null,
            br.com.imarui.ima.identity.core.domain.model.identity.service.ServiceName.from(
                "No mailbox"),
            null,
            Instant.now());
    i.assignNickname("no-mailbox");
    i =
        (br.com.imarui.ima.identity.core.domain.model.identity.service.ServiceIdentity)
            identities.save(i);
    complete(i);
    assertNotNull(authentication.login(IdentityKind.SERVICE, "no-mailbox", PASSWORD));
  }

  @Test
  void notificationJobRetriesWithoutStoringRawSecret() {
    var i = pending(IdentityKind.SERVICE, "mail-service");
    authentication.request(IdentityKind.SERVICE, "mail-service", Purpose.ACCOUNT_ACTIVATION);
    doThrow(new IllegalStateException("simulated delivery failure"))
        .when(mail)
        .send(anyString(), anyString(), any());
    assertTrue(delivery.processOne());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select attempts from authentication_notification_jobs where identity_id=?",
            Integer.class,
            i.getId().value()));
    assertNull(
        jdbc.queryForObject(
            "select delivered_at from authentication_notification_jobs where identity_id=?",
            java.sql.Timestamp.class,
            i.getId().value()));
    reset(mail);
    jdbc.update(
        "update authentication_notification_jobs set next_attempt_at=current_timestamp where"
            + " identity_id=?",
        i.getId().value());
    assertTrue(delivery.processOne());
    verify(mail).send(eq("service@example.test"), anyString(), eq(Purpose.ACCOUNT_ACTIVATION));
  }

  @Test
  void bootstrapIsOneShotAndRootHasRealAuthorities() throws Exception {
    Path dir = Files.createTempDirectory("iiams-bootstrap-test");
    Path input = dir.resolve("input.json");
    Path output = dir.resolve("invite.json");
    Files.writeString(
        input,
        "{\"kind\":\"SERVICE\",\"nickname\":\"root-operator\",\"name\":\"Initial operator\"}");
    var provisioning = getProvisioning();
    var runner =
        new AuthenticationBootstrap(
            provisioning,
            authentication,
            jdbc,
            json,
            transactions,
            Clock.systemUTC(),
            input.toString(),
            output.toString());
    try {
      runner.run(new DefaultApplicationArguments());
      var result = json.readTree(Files.readString(output));
      String invitation = result.path("invitation").asText();
      authentication.complete(invitation, PASSWORD, Purpose.ACCOUNT_ACTIVATION);
      var login = authentication.login(IdentityKind.SERVICE, "root-operator", PASSWORD);
      assertEquals(
          200,
          http("GET", "/authentication/admin/sessions", null, login.accessToken()).statusCode());
      var again =
          new AuthenticationBootstrap(
              provisioning,
              authentication,
              jdbc,
              json,
              transactions,
              Clock.systemUTC(),
              input.toString(),
              dir.resolve("again.json").toString());
      assertThrows(IllegalStateException.class, () -> again.run(new DefaultApplicationArguments()));
    } finally {
      Files.deleteIfExists(output);
      Files.deleteIfExists(input);
      Files.deleteIfExists(dir.resolve("again.json"));
      Files.deleteIfExists(dir);
      jdbc.update("delete from authentication_bootstrap where name='initial-root'");
    }
  }

  @Autowired br.com.imarui.ima.identity.api.module.InitialIdentityProvisioning provisioning;

  private br.com.imarui.ima.identity.api.module.InitialIdentityProvisioning getProvisioning() {
    return provisioning;
  }

  @Test
  void webCookieCsrfAndNoRefreshInJson() throws Exception {
    var i = pending(IdentityKind.SERVICE, "web-service");
    complete(i);
    var noCsrf =
        http(
            "POST",
            "/authentication/login",
            "{\"identityKind\":\"SERVICE\",\"identifier\":\"web-service\",\"password\":\""
                + PASSWORD
                + "\"}",
            null);
    assertEquals(403, noCsrf.statusCode());
    var csrf = http("GET", "/authentication/csrf", null, null);
    String cookie = csrf.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    String proof = json.readTree(csrf.body()).path("token").asText();
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/authentication/login"))
            .header("Content-Type", "application/json")
            .header("Cookie", cookie)
            .header("X-XSRF-TOKEN", proof)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"identityKind\":\"SERVICE\",\"identifier\":\"web-service\",\"password\":\""
                        + PASSWORD
                        + "\"}"))
            .build();
    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    var body = json.readTree(response.body());
    assertFalse(body.has("refreshToken"));
    assertEquals(900, body.path("expiresIn").asInt());
    String refreshCookie = response.headers().firstValue("Set-Cookie").orElseThrow();
    assertTrue(refreshCookie.contains("HttpOnly"));
    assertTrue(refreshCookie.contains("Secure"));
    assertTrue(refreshCookie.contains("SameSite=Lax"));
    assertTrue(refreshCookie.contains("Path=/authentication"));
    assertFalse(refreshCookie.contains("Domain="));
    var jwks = http("GET", "/.well-known/jwks.json", null, null);
    assertEquals(200, jwks.statusCode());
    assertFalse(jwks.body().contains("\"d\":"));
    assertFalse(jwks.body().contains("PRIVATE"));
  }

  @Test
  void jwtRejectsWrongIssuerAudienceSignatureAndExpiration() throws Exception {
    var i = pending(IdentityKind.SERVICE, "jwt-security");
    complete(i);
    var login = authentication.login(IdentityKind.SERVICE, "jwt-security", PASSWORD);
    var claims =
        Jwts.parser()
            .verifyWith(keys.publicKeys().get(keys.kid()))
            .build()
            .parseSignedClaims(login.accessToken())
            .getPayload();
    for (String invalid : List.of("issuer", "audience", "expiration", "subject", "sid")) {
      var builder = Jwts.builder().claims(claims).header().keyId(keys.kid()).type("at+jwt").and();
      switch (invalid) {
        case "issuer" -> builder.issuer("wrong");
        case "audience" -> builder.claim("aud", List.of("wrong"));
        case "expiration" -> builder.expiration(Date.from(Instant.now().minusSeconds(1)));
        case "subject" -> builder.subject("123");
        case "sid" -> builder.claim("sid", UUID.randomUUID().toString());
      }
      assertEquals(
          401,
          http(
                  "GET",
                  "/api/v1/identities/" + i.getId(),
                  null,
                  builder.signWith(keys.getKey(), Jwts.SIG.RS256).compact())
              .statusCode(),
          invalid);
    }
    var pair = java.security.KeyPairGenerator.getInstance("RSA");
    pair.initialize(2048);
    var other = pair.generateKeyPair();
    String forged =
        Jwts.builder()
            .claims(claims)
            .header()
            .keyId(keys.kid())
            .type("at+jwt")
            .and()
            .signWith(other.getPrivate(), Jwts.SIG.RS256)
            .compact();
    assertEquals(401, http("GET", "/api/v1/identities/" + i.getId(), null, forged).statusCode());
    authentication.logout(login.refreshToken());
    // Offline consumers deliberately continue to accept this cryptographically valid JWT until exp.
    assertEquals(
        i.getId().toString(),
        Jwts.parser()
            .verifyWith(keys.publicKeys().get(keys.kid()))
            .build()
            .parseSignedClaims(login.accessToken())
            .getPayload()
            .getSubject());
  }

  @Test
  void outboxFailureRollsBackEffectsAndRetrySucceeds() {
    var i = pending(IdentityKind.SERVICE, "outbox-rollback");
    complete(i);
    var login = authentication.login(IdentityKind.SERVICE, "outbox-rollback", PASSWORD);
    disable.execute(new DisableIdentityCommand(i.getId()));
    jdbc.execute(
        "create function fail_processed() returns trigger language plpgsql as $$ begin raise"
            + " exception 'simulated processing failure'; end $$");
    jdbc.execute(
        "create trigger fail_processed before update on ima_access_revocation_requests for each row"
            + " execute function fail_processed()");
    try {
      assertThrows(RuntimeException.class, () -> processor.process());
      assertEquals(
          1,
          jdbc.queryForObject(
              "select count(*) from authentication_sessions where identity_id=? and"
                  + " session_status='ACTIVE'",
              Integer.class,
              i.getId().value()));
      assertEquals(
          1,
          jdbc.queryForObject(
              "select count(*) from ima_access_revocation_requests where subject_id=? and"
                  + " processed_at is null",
              Integer.class,
              i.getId().value()));
    } finally {
      jdbc.execute("drop trigger fail_processed on ima_access_revocation_requests");
      jdbc.execute("drop function fail_processed()");
    }
    assertEquals(1, processor.process());
    assertThrows(AuthenticationFailure.class, () -> authentication.refresh(login.refreshToken()));
  }

  @Test
  void corsAllowlistAndGenericRecoveryThrottleAreEnforced() throws Exception {
    var client = HttpClient.newHttpClient();
    for (var origin : List.of("https://frontend.example.test", "https://untrusted.example.test")) {
      var req =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/authentication/login"))
              .header("Origin", origin)
              .header("Access-Control-Request-Method", "POST")
              .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
              .build();
      var response = client.send(req, HttpResponse.BodyHandlers.ofString());
      assertEquals(origin.contains("untrusted") ? 403 : 200, response.statusCode());
      if (!origin.contains("untrusted")) {
        assertEquals(
            origin, response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertEquals(
            "true",
            response.headers().firstValue("Access-Control-Allow-Credentials").orElseThrow());
      }
    }
    jdbc.execute("truncate authentication_rate_limits");
    String request = "{\"identityKind\":\"SERVICE\",\"identifier\":\"unknown-name\"}";
    for (int n = 0; n < 5; n++)
      assertEquals(
          202, http("POST", "/authentication/recovery/request", request, null).statusCode());
    var limited = http("POST", "/authentication/recovery/request", request, null);
    assertEquals(429, limited.statusCode());
    assertTrue(
        limited
            .headers()
            .firstValue("Content-Type")
            .orElse("")
            .startsWith("application/problem+json"));
  }

  @ParameterizedTest
  @EnumSource(IdentityKind.class)
  void completeBrowserFlowForEveryKind(IdentityKind kind) throws Exception {
    var identity = pending(kind, "browser-flow");
    String invitation =
        authentication.issueChallenge(identity.getId().value(), Purpose.ACCOUNT_ACTIVATION);
    var activation =
        http(
            "POST",
            "/authentication/activation/complete",
            json.writeValueAsString(Map.of("token", invitation, "password", PASSWORD)),
            null);
    assertEquals(204, activation.statusCode(), activation.body());
    var csrf = http("GET", "/authentication/csrf", null, null);
    String csrfCookie = csrf.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    String proof = json.readTree(csrf.body()).path("token").asText();
    var login =
        browser(
            "/authentication/login",
            json.writeValueAsString(
                Map.of(
                    "identityKind",
                    kind.name(),
                    "identifier",
                    "browser-flow",
                    "password",
                    PASSWORD)),
            csrfCookie,
            proof);
    assertEquals(200, login.statusCode(), login.body());
    String refresh = login.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    var rotated = browser("/authentication/refresh", "{}", csrfCookie + "; " + refresh, proof);
    assertEquals(200, rotated.statusCode(), rotated.body());
    assertFalse(json.readTree(rotated.body()).has("refreshToken"));
    String replacement = rotated.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    assertNotEquals(refresh, replacement);
    var logout = browser("/authentication/logout", "{}", csrfCookie + "; " + replacement, proof);
    assertEquals(204, logout.statusCode());
    assertTrue(logout.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=0"));
    assertEquals(
        401,
        browser("/authentication/refresh", "{}", csrfCookie + "; " + replacement, proof)
            .statusCode());
  }

  private HttpResponse<String> browser(String path, String body, String cookie, String csrf)
      throws Exception {
    var req =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header("Cookie", cookie)
            .header("X-XSRF-TOKEN", csrf)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void permissionClaimsDoNotGrantAdministrativeAccess() throws Exception {
    var identity = pending(IdentityKind.SERVICE, "no-admin-role");
    complete(identity);
    var login = authentication.login(IdentityKind.SERVICE, "no-admin-role", PASSWORD);
    var claims =
        Jwts.parser()
            .verifyWith(keys.publicKeys().get(keys.kid()))
            .build()
            .parseSignedClaims(login.accessToken())
            .getPayload();
    var injected =
        Jwts.builder()
            .claims(claims)
            .header()
            .keyId(keys.kid())
            .type("at+jwt")
            .and()
            .claim("permissions", List.of("AUTHENTICATION_SESSION_READ"))
            .signWith(keys.getKey(), Jwts.SIG.RS256)
            .compact();
    assertEquals(403, http("GET", "/authentication/admin/sessions", null, injected).statusCode());
  }

  @Test
  void authenticatedPasswordChangeRequiresCurrentPasswordAndRevokesSession() {
    var i = pending(IdentityKind.SERVICE, "password-change");
    complete(i);
    var login = authentication.login(IdentityKind.SERVICE, "password-change", PASSWORD);
    assertThrows(
        AuthenticationFailure.class,
        () ->
            authentication.changePassword(
                i.getId().value(), "incorrect", "New strong password 987!"));
    authentication.changePassword(i.getId().value(), PASSWORD, "New strong password 987!");
    assertThrows(AuthenticationFailure.class, () -> authentication.refresh(login.refreshToken()));
    assertThrows(
        AuthenticationFailure.class,
        () -> authentication.login(IdentityKind.SERVICE, "password-change", PASSWORD));
    assertNotNull(
        authentication.login(IdentityKind.SERVICE, "password-change", "New strong password 987!"));
  }

  @Test
  void activationFailureRollsBackCredentialChallengeAndIdentity() {
    var i = pending(IdentityKind.SERVICE, "atomic-activation");
    String invite = authentication.issueChallenge(i.getId().value(), Purpose.ACCOUNT_ACTIVATION);
    jdbc.execute(
        "create function fail_activation() returns trigger language plpgsql as $$ begin if"
            + " new.status='ACTIVE' and old.status='PENDING' then raise exception 'simulated"
            + " activation failure'; end if; return new; end $$");
    jdbc.execute(
        "create trigger fail_activation before update on ima_identities for each row execute"
            + " function fail_activation()");
    try {
      assertThrows(
          RuntimeException.class,
          () -> authentication.complete(invite, PASSWORD, Purpose.ACCOUNT_ACTIVATION));
      assertTrue(identities.findById(i.getId()).orElseThrow().isPending());
      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from authentication_credentials where identity_id=?",
              Integer.class,
              i.getId().value()));
      assertNull(
          jdbc.queryForObject(
              "select consumed_at from authentication_challenges where identity_id=?",
              java.sql.Timestamp.class,
              i.getId().value()));
    } finally {
      jdbc.execute("drop trigger fail_activation on ima_identities");
      jdbc.execute("drop function fail_activation()");
    }
    authentication.complete(invite, PASSWORD, Purpose.ACCOUNT_ACTIVATION);
    assertNotNull(authentication.login(IdentityKind.SERVICE, "atomic-activation", PASSWORD));
  }

  @Test
  void concurrentDisableAndRefreshLeaveNoRenewableOldSession() throws Exception {
    var i = pending(IdentityKind.SERVICE, "disable-race");
    complete(i);
    var login = authentication.login(IdentityKind.SERVICE, "disable-race", PASSWORD);
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      var refreshing =
          pool.submit(
              () -> {
                start.await();
                try {
                  authentication.refresh(login.refreshToken());
                } catch (AuthenticationFailure expected) {
                }
                return true;
              });
      var disabling =
          pool.submit(
              () -> {
                start.await();
                disable.execute(new DisableIdentityCommand(i.getId()));
                return true;
              });
      start.countDown();
      assertTrue(refreshing.get(15, TimeUnit.SECONDS));
      assertTrue(disabling.get(15, TimeUnit.SECONDS));
      processor.process();
      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from authentication_sessions where identity_id=? and"
                  + " session_status='ACTIVE'",
              Integer.class,
              i.getId().value()));
      assertThrows(AuthenticationFailure.class, () -> authentication.refresh(login.refreshToken()));
    } finally {
      pool.shutdownNow();
    }
  }
}
