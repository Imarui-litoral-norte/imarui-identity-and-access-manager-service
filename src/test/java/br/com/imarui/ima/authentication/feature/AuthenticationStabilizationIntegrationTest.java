package br.com.imarui.ima.authentication.feature;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.imarui.ima.authentication.core.application.exception.AuthenticationFailure;
import br.com.imarui.ima.authentication.core.domain.model.AuthenticationChallenge.Purpose;
import br.com.imarui.ima.authentication.core.port.*;
import br.com.imarui.ima.authentication.infra.mail.ChallengeDeliveryWorker;
import br.com.imarui.ima.identity.core.application.command.identityapplication.*;
import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.*;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

class AuthenticationStabilizationIntegrationTest extends IdentityIntegrationSupport {
  @Autowired ChallengeDeliveryWorker worker;
  @MockitoBean ChallengeMail mail;
  @Autowired IdentityAccess identityAccess;
  @Autowired Clock clock;
  static final String PASSWORD = "Strong initial password 123!";
  static final String NEXT = "Strong replacement password 456!";

  Identity pending(String nickname) {
    var i = service("shared@example.test");
    i.assignNickname(nickname);
    return identities.save(i);
  }

  Identity active(String nickname) {
    var i = pending(nickname);
    authentication.complete(
        authentication.issueChallenge(i.getId().value(), Purpose.ACCOUNT_ACTIVATION),
        PASSWORD,
        Purpose.ACCOUNT_ACTIVATION);
    return i;
  }

  UUID sid(String refresh) {
    return jdbc.queryForObject(
        "select s.public_id from authentication_sessions s join authentication_refresh_tokens t on"
            + " t.session_id=s.id where t.token_hash=?",
        UUID.class,
        hashes.hash(refresh));
  }

  @Autowired RefreshTokenHashService hashes;

  void due() {
    jdbc.update(
        "update authentication_notification_jobs set next_attempt_at=current_timestamp-interval '1"
            + " second' where state in ('PENDING','PROCESSING')");
  }

  String state(UUID id) {
    return jdbc.queryForObject(
        "select state from authentication_notification_jobs where identity_id=? order by"
            + " next_attempt_at desc limit 1",
        String.class,
        id);
  }

  @ParameterizedTest
  @EnumSource(IdentityKind.class)
  void sharedEmailAllowsTwoRegistrationsAndDocumentNicknameUniquenessRemains(IdentityKind kind) {
    CreateIdentityApplicationCommand a =
        switch (kind) {
          case PERSON ->
              new CreatePersonIdentityApplicationCommand(
                  "Person One", "52998224725", "shared@example.test", null, null, "account-one");
          case LEGAL_ENTITY ->
              new CreateLegalEntityIdentityApplicationCommand(
                  "Company One", null, "11222333000181", "shared@example.test", "account-one");
          case SERVICE ->
              new CreateServiceIdentityApplicationCommand(
                  "Service One", null, "shared@example.test", "account-one");
        };
    CreateIdentityApplicationCommand b =
        switch (kind) {
          case PERSON ->
              new CreatePersonIdentityApplicationCommand(
                  "Person Two", "11144477735", "shared@example.test", null, null, "account-two");
          case LEGAL_ENTITY ->
              new CreateLegalEntityIdentityApplicationCommand(
                  "Company Two", null, "04252011000110", "shared@example.test", "account-two");
          case SERVICE ->
              new CreateServiceIdentityApplicationCommand(
                  "Service Two", null, "shared@example.test", "account-two");
        };
    var first = create.execute(a);
    var second = create.execute(b);
    assertNotEquals(first.id(), second.id());
    var x =
        approve.execute(
            approval(
                IdentityApplicationId.from(first.id()),
                br.com.imarui.ima.identity.core.domain.enums.affiliation.AffiliationType.EXTERNAL,
                null));
    var y =
        approve.execute(
            approval(
                IdentityApplicationId.from(second.id()),
                br.com.imarui.ima.identity.core.domain.enums.affiliation.AffiliationType.EXTERNAL,
                null));
    assertNotEquals(x.resolvedIdentityId(), y.resolvedIdentityId());
    assertThrows(
        br.com.imarui.ima.identity.core.application.exception.IdentityConflictException.class,
        () -> create.execute(a));
    assertEquals(
        2,
        jdbc.queryForObject(
            "select count(*) from ima_identities where primary_email='shared@example.test'",
            Integer.class));
  }

  @ParameterizedTest
  @EnumSource(IdentityKind.class)
  void missingOrInvalidEmailAndMissingNicknameAreRejectedForEveryKind(IdentityKind kind)
      throws Exception {
    var body = new LinkedHashMap<String, Object>();
    body.put("type", kind);
    body.put("nickname", "contact-required");
    if (kind == IdentityKind.PERSON) {
      body.put("fullName", "Person Test");
      body.put("cpf", "52998224725");
    }
    if (kind == IdentityKind.LEGAL_ENTITY) {
      body.put("legalName", "Company Test");
      body.put("cnpj", "11222333000181");
    }
    if (kind == IdentityKind.SERVICE) body.put("name", "Service Test");
    for (String email : List.of("", "invalid", " ")) {
      body.put("email", email);
      assertEquals(
          400,
          http("POST", "/api/v1/identity-applications", json.writeValueAsString(body), null)
              .statusCode());
    }
    body.remove("email");
    assertEquals(
        400,
        http("POST", "/api/v1/identity-applications", json.writeValueAsString(body), null)
            .statusCode());
    body.put("email", "valid@example.test");
    body.remove("nickname");
    assertEquals(
        400,
        http("POST", "/api/v1/identity-applications", json.writeValueAsString(body), null)
            .statusCode());
  }

  @Test
  void emailUpdateMayReuseAnotherIdentityContact() {
    var a = pending("contact-a");
    var b = pending("contact-b");
    b.changePrimaryEmail(
        IdentityEmail.create(Email.from("other@example.test"), EmailType.PRIMARY, Instant.now()),
        Instant.now());
    identities.save(b);
    b.changePrimaryEmail(
        IdentityEmail.create(
            Email.from(a.getPrimaryEmail().email().value()), EmailType.PRIMARY, Instant.now()),
        Instant.now());
    identities.save(b);
    assertEquals(
        2,
        jdbc.queryForObject(
            "select count(*) from ima_identities where primary_email='shared@example.test'",
            Integer.class));
  }

  @Test
  void recoveryWithSharedContactSelectsOnlyKindAndNickname() {
    var a = active("shared-a");
    var b = active("shared-b");
    authentication.request(IdentityKind.SERVICE, "shared-b", Purpose.PASSWORD_RESET);
    assertEquals(
        List.of(b.getId().value()),
        jdbc.queryForList("select identity_id from authentication_notification_jobs", UUID.class));
    authentication.request(IdentityKind.SERVICE, "shared@example.test", Purpose.PASSWORD_RESET);
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from authentication_notification_jobs", Integer.class));
    assertTrue(worker.processOne());
    var secret = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(mail).send(eq("shared@example.test"), secret.capture(), eq(Purpose.PASSWORD_RESET));
    authentication.complete(secret.getValue(), NEXT, Purpose.PASSWORD_RESET);
    assertNotNull(authentication.login(IdentityKind.SERVICE, "shared-a", PASSWORD));
    assertNotNull(authentication.login(IdentityKind.SERVICE, "shared-b", NEXT));
  }

  @ParameterizedTest
  @EnumSource(Purpose.class)
  void newerLinkNeverInvalidatesOlderAndSuccessfulConsumptionInvalidatesSiblings(Purpose purpose) {
    var i = purpose == Purpose.ACCOUNT_ACTIVATION ? pending("links") : active("links");
    var a = authentication.issueChallenge(i.getId().value(), purpose);
    var b = authentication.issueChallenge(i.getId().value(), purpose);
    assertNull(authenticationStore.challenge(hashes.hash(a), false).orElseThrow().consumedAt());
    assertNull(authenticationStore.challenge(hashes.hash(b), false).orElseThrow().consumedAt());
    authentication.complete(a, NEXT, purpose);
    assertThrows(AuthenticationFailure.class, () -> authentication.complete(b, PASSWORD, purpose));
    assertThrows(AuthenticationFailure.class, () -> authentication.complete(a, PASSWORD, purpose));
    assertNotNull(authentication.login(IdentityKind.SERVICE, "links", NEXT));
  }

  @ParameterizedTest
  @EnumSource(Purpose.class)
  void newestLinkMayWinAndFailedDeliveryLeavesOlderLinkUsable(Purpose purpose) {
    var i = purpose == Purpose.ACCOUNT_ACTIVATION ? pending("smtp-links") : active("smtp-links");
    var a = authentication.issueChallenge(i.getId().value(), purpose);
    authentication.request(IdentityKind.SERVICE, "smtp-links", purpose);
    doThrow(new IllegalStateException("SMTP unavailable"))
        .when(mail)
        .send(anyString(), anyString(), any());
    assertTrue(worker.processOne());
    assertNull(authenticationStore.challenge(hashes.hash(a), false).orElseThrow().consumedAt());
    reset(mail);
    due();
    assertTrue(worker.processOne());
    var secret = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(mail).send(anyString(), secret.capture(), eq(purpose));
    assertNull(authenticationStore.challenge(hashes.hash(a), false).orElseThrow().consumedAt());
    authentication.complete(secret.getValue(), NEXT, purpose);
    assertThrows(AuthenticationFailure.class, () -> authentication.complete(a, PASSWORD, purpose));
  }

  @ParameterizedTest
  @EnumSource(Purpose.class)
  void distinctConcurrentLinksHaveExactlyOneWinner(Purpose purpose) throws Exception {
    var i = purpose == Purpose.ACCOUNT_ACTIVATION ? pending("race-links") : active("race-links");
    var a = authentication.issueChallenge(i.getId().value(), purpose);
    var b = authentication.issueChallenge(i.getId().value(), purpose);
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var futures = new ArrayList<Future<Boolean>>();
      for (String raw : List.of(a, b))
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    authentication.complete(raw, NEXT, purpose);
                    return true;
                  } catch (AuthenticationFailure denied) {
                    return false;
                  }
                }));
      start.countDown();
      assertNotEquals(
          futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
  }

  @ParameterizedTest
  @EnumSource(Purpose.class)
  void completeRollbackPreservesBothLinksAndCredential(Purpose purpose) {
    var i =
        purpose == Purpose.ACCOUNT_ACTIVATION
            ? pending("rollback-links")
            : active("rollback-links");
    var a = authentication.issueChallenge(i.getId().value(), purpose);
    var b = authentication.issueChallenge(i.getId().value(), purpose);
    assertThrows(
        IllegalStateException.class,
        () ->
            new TransactionTemplate(transactions)
                .executeWithoutResult(
                    status -> {
                      authentication.complete(a, NEXT, purpose);
                      throw new IllegalStateException("rollback");
                    }));
    assertNull(authenticationStore.challenge(hashes.hash(a), false).orElseThrow().consumedAt());
    assertNull(authenticationStore.challenge(hashes.hash(b), false).orElseThrow().consumedAt());
    if (purpose == Purpose.PASSWORD_RESET)
      assertNotNull(authentication.login(IdentityKind.SERVICE, "rollback-links", PASSWORD));
    authentication.complete(b, NEXT, purpose);
  }

  @Test
  void notificationStopsAtFiveFailuresAndAllowsNewJob() {
    var i = pending("mail-five");
    authentication.request(IdentityKind.SERVICE, "mail-five", Purpose.ACCOUNT_ACTIVATION);
    doThrow(new IllegalStateException("failed secret must not persist"))
        .when(mail)
        .send(anyString(), anyString(), any());
    for (int n = 1; n <= 5; n++) {
      due();
      assertTrue(worker.processOne());
      assertEquals(
          n,
          jdbc.queryForObject(
              "select attempts from authentication_notification_jobs where identity_id=?",
              Integer.class,
              i.getId().value()));
    }
    assertEquals("FAILED", state(i.getId().value()));
    due();
    assertFalse(worker.processOne());
    verify(mail, times(5)).send(anyString(), anyString(), any());
    authentication.request(IdentityKind.SERVICE, "mail-five", Purpose.ACCOUNT_ACTIVATION);
    reset(mail);
    assertTrue(worker.processOne());
    assertEquals(
        2,
        jdbc.queryForObject(
            "select count(*) from authentication_notification_jobs where identity_id=?",
            Integer.class,
            i.getId().value()));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from authentication_notification_jobs where state='DELIVERED'",
            Integer.class));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from authentication_audit where event='NOTIFICATION_FAILED' and"
                + " identity_id=?",
            Integer.class,
            i.getId().value()));
  }

  @Test
  void notificationBackoffIsProgressiveAndReservationSurvivesWorkerRestart() {
    var i = pending("mail-restart");
    authentication.request(IdentityKind.SERVICE, "mail-restart", Purpose.ACCOUNT_ACTIVATION);
    doThrow(new IllegalStateException("offline")).when(mail).send(anyString(), anyString(), any());
    for (long delay : List.of(30L, 120L, 600L, 1800L, 3600L)) {
      due();
      var before = Instant.now();
      assertTrue(worker.processOne());
      var next =
          jdbc.queryForObject(
                  "select next_attempt_at from authentication_notification_jobs where"
                      + " identity_id=?",
                  java.sql.Timestamp.class,
                  i.getId().value())
              .toInstant();
      assertTrue(Duration.between(before, next).getSeconds() >= delay - 1);
      assertFalse(worker.processOne());
    }
    verify(mail, times(5)).send(anyString(), anyString(), any());
    jdbc.update(
        "update authentication_notification_jobs set"
            + " state='PROCESSING',attempts=4,next_attempt_at=current_timestamp-interval '1 second' where"
            + " identity_id=?",
        i.getId().value());
    reset(mail);
    var restarted =
        new ChallengeDeliveryWorker(
            jdbc, authentication, identityAccess, mail, clock, transactions);
    assertTrue(restarted.processOne());
    verify(mail, times(1)).send(anyString(), anyString(), any());
    jdbc.update(
        "update authentication_notification_jobs set"
            + " state='PROCESSING',attempts=5,next_attempt_at=current_timestamp-interval '1 second' where"
            + " identity_id=?",
        i.getId().value());
    reset(mail);
    assertFalse(restarted.processOne());
    assertEquals("FAILED", state(i.getId().value()));
    verifyNoInteractions(mail);
  }

  @Test
  void twoWorkersCannotSendSameJobEvenIfReservationDeadlineHasPassed() throws Exception {
    pending("two-workers");
    authentication.request(IdentityKind.SERVICE, "two-workers", Purpose.ACCOUNT_ACTIVATION);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            call -> {
              entered.countDown();
              assertTrue(release.await(15, TimeUnit.SECONDS));
              return null;
            })
        .when(mail)
        .send(anyString(), anyString(), any());
    var pool = Executors.newSingleThreadExecutor();
    try {
      var first = pool.submit(worker::processOne);
      assertTrue(entered.await(10, TimeUnit.SECONDS));
      due();
      assertFalse(worker.processOne());
      release.countDown();
      assertTrue(first.get(15, TimeUnit.SECONDS));
      verify(mail, times(1)).send(anyString(), anyString(), any());
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void notificationPersistsHashesAndMetadataOnly() {
    var i = pending("no-secret");
    authentication.request(IdentityKind.SERVICE, "no-secret", Purpose.ACCOUNT_ACTIVATION);
    assertTrue(worker.processOne());
    var raw = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(mail).send(anyString(), raw.capture(), any());
    assertEquals(
        hashes.hash(raw.getValue()),
        jdbc.queryForObject(
            "select token_hash from authentication_challenges where identity_id=?",
            String.class,
            i.getId().value()));
    var persisted =
        jdbc.queryForList(
                    "select row_to_json(j)::text as data from authentication_notification_jobs j")
                .toString()
            + jdbc.queryForList("select row_to_json(a)::text as data from authentication_audit a");
    assertFalse(persisted.contains(raw.getValue()));
    assertFalse(persisted.contains(hashes.hash(raw.getValue())));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void mismatchedLogoutIsUnauthorizedAndHasNoEffects(boolean differentOwner) throws Exception {
    var a = active("logout-a");
    var b = differentOwner ? active("logout-b") : a;
    var x = authentication.login(IdentityKind.SERVICE, "logout-a", PASSWORD);
    var y =
        authentication.login(
            IdentityKind.SERVICE, differentOwner ? "logout-b" : "logout-a", PASSWORD);
    var response = logoutHttp(x.accessToken(), y.refreshToken());
    assertEquals(401, response.statusCode(), response.body());
    assertNotNull(authentication.refresh(x.refreshToken()));
    assertNotNull(authentication.refresh(y.refreshToken()));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from authentication_audit where event='LOGOUT_MISMATCH' and"
                + " actor_identity_id=?",
            Integer.class,
            a.getId().value()));
  }

  @Test
  void validCombinedAccessOnlyCookieOnlyAndRepeatedCookieLogout() throws Exception {
    active("logout-valid");
    var a = authentication.login(IdentityKind.SERVICE, "logout-valid", PASSWORD);
    assertEquals(204, logoutHttp(a.accessToken(), a.refreshToken()).statusCode());
    assertThrows(AuthenticationFailure.class, () -> authentication.refresh(a.refreshToken()));
    assertEquals(204, logoutHttp(null, a.refreshToken()).statusCode());
    var b = authentication.login(IdentityKind.SERVICE, "logout-valid", PASSWORD);
    assertEquals(204, logoutHttp(b.accessToken(), null).statusCode());
    assertThrows(AuthenticationFailure.class, () -> authentication.refresh(b.refreshToken()));
    var c = authentication.login(IdentityKind.SERVICE, "logout-valid", PASSWORD);
    assertEquals(204, logoutHttp(null, c.refreshToken()).statusCode());
    assertThrows(AuthenticationFailure.class, () -> authentication.refresh(c.refreshToken()));
  }

  @Test
  void invalidCookieCannotRevokeBearerSessionAndFailureRollsBackLogout() throws Exception {
    var i = active("logout-rollback");
    var issued = authentication.login(IdentityKind.SERVICE, "logout-rollback", PASSWORD);
    assertEquals(401, logoutHttp(issued.accessToken(), "invalid-cookie").statusCode());
    assertThrows(
        IllegalStateException.class,
        () ->
            new TransactionTemplate(transactions)
                .executeWithoutResult(
                    status -> {
                      authentication.logout(
                          i.getId().value(), sid(issued.refreshToken()), issued.refreshToken());
                      throw new IllegalStateException("rollback");
                    }));
    assertNotNull(authentication.refresh(issued.refreshToken()));
  }

  @Test
  void expiredBearerIsRejectedAndClientCanLogoutByOmittingIt() throws Exception {
    active("logout-expired");
    var issued = authentication.login(IdentityKind.SERVICE, "logout-expired", PASSWORD);
    var claims =
        io.jsonwebtoken.Jwts.parser()
            .verifyWith(keys.publicKeys().get(keys.kid()))
            .build()
            .parseSignedClaims(issued.accessToken())
            .getPayload();
    var expired =
        io.jsonwebtoken.Jwts.builder()
            .claims(claims)
            .header()
            .keyId(keys.kid())
            .type("at+jwt")
            .and()
            .expiration(Date.from(Instant.now().minusSeconds(120)))
            .signWith(keys.getKey(), io.jsonwebtoken.Jwts.SIG.RS256)
            .compact();
    assertEquals(401, logoutHttp(expired, issued.refreshToken()).statusCode());
    assertEquals(204, logoutHttp(null, issued.refreshToken()).statusCode());
  }

  @Test
  void administrativeInvitationAuditsActorTargetAndTrustedSocketOrigin() throws Exception {
    var target = pending("invited");
    var bearer = token(reviewer.getId(), "AUTHENTICATION_INVITATION_CREATE");
    var response =
        http(
            "POST",
            "/authentication/admin/identities/" + target.getId() + "/invitation",
            null,
            bearer);
    assertEquals(200, response.statusCode(), response.body());
    var raw = json.readTree(response.body()).path("invitation").asText();
    var audit =
        jdbc.queryForMap(
            "select * from authentication_audit where event='ADMIN_INVITATION' and"
                + " target_identity_id=?",
            target.getId().value());
    assertEquals(reviewer.getId().value(), audit.get("actor_identity_id"));
    assertEquals("SUCCESS", audit.get("result"));
    assertNotNull(audit.get("request_origin"));
    assertFalse(audit.toString().contains(raw));
    assertFalse(audit.toString().contains(hashes.hash(raw)));
    assertTrue(response.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
    assertEquals(
        401,
        http(
                "POST",
                "/authentication/admin/identities/" + UUID.randomUUID() + "/invitation",
                null,
                bearer)
            .statusCode());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from authentication_challenges where identity_id=?",
            Integer.class,
            target.getId().value()));
  }

  @Test
  void unauthorizedAdminCannotIssueInvitationAndPublicRequestHasDistinctEvent() throws Exception {
    var target = pending("denied-invite");
    var bearer = token(reviewer.getId());
    assertEquals(
        403,
        http(
                "POST",
                "/authentication/admin/identities/" + target.getId() + "/invitation",
                null,
                bearer)
            .statusCode());
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from authentication_challenges where identity_id=?",
            Integer.class,
            target.getId().value()));
    authentication.request(IdentityKind.SERVICE, "denied-invite", Purpose.ACCOUNT_ACTIVATION);
    worker.processOne();
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from authentication_audit where event='ADMIN_INVITATION' and"
                + " target_identity_id=?",
            Integer.class,
            target.getId().value()));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from authentication_audit where event='CHALLENGE_ISSUED' and"
                + " identity_id=?",
            Integer.class,
            target.getId().value()));
  }

  @Test
  void completingResetPreservesChallengesOfOtherPurposes() {
    var i = active("purpose-isolation");
    var now = Instant.now();
    var raw = "other-purpose-secret-value-1234567890";
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status ->
                authenticationStore.addChallenge(
                    new br.com.imarui.ima.authentication.core.domain.model.AuthenticationChallenge(
                        UUID.randomUUID(),
                        i.getId().value(),
                        Purpose.ACCOUNT_ACTIVATION,
                        hashes.hash(raw),
                        now,
                        now.plusSeconds(600),
                        null)));
    var reset = authentication.issueChallenge(i.getId().value(), Purpose.PASSWORD_RESET);
    authentication.complete(reset, NEXT, Purpose.PASSWORD_RESET);
    assertNull(authenticationStore.challenge(hashes.hash(raw), false).orElseThrow().consumedAt());
  }

  @Test
  void forwardedHeadersCannotSpoofRecordedAdministrativeOrigin() throws Exception {
    var target = pending("origin-test");
    var bearer = token(reviewer.getId(), "AUTHENTICATION_INVITATION_CREATE");
    var req =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + port
                        + "/authentication/admin/identities/"
                        + target.getId()
                        + "/invitation"))
            .header("Authorization", "Bearer " + bearer)
            .header("X-Forwarded-For", "198.51.100.77")
            .header("Forwarded", "for=198.51.100.77;proto=https")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    assertEquals(
        200,
        HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString()).statusCode());
    assertNotEquals(
        "198.51.100.77",
        jdbc.queryForObject(
            "select request_origin from authentication_audit where event='ADMIN_INVITATION' and"
                + " target_identity_id=?",
            String.class,
            target.getId().value()));
  }

  @ParameterizedTest
  @EnumSource(Purpose.class)
  void failedSmtpAttemptLeavesPreviouslyDeliveredLinkConsumable(Purpose purpose) {
    var i =
        purpose == Purpose.ACCOUNT_ACTIVATION ? pending("previous-link") : active("previous-link");
    var original = authentication.issueChallenge(i.getId().value(), purpose);
    authentication.request(IdentityKind.SERVICE, "previous-link", purpose);
    doThrow(new IllegalStateException("SMTP failure"))
        .when(mail)
        .send(anyString(), anyString(), any());
    assertTrue(worker.processOne());
    authentication.complete(original, NEXT, purpose);
    assertNotNull(authentication.login(IdentityKind.SERVICE, "previous-link", NEXT));
  }

  @Test
  void knownAccountRequestsAreThrottledAndCoalesced() throws Exception {
    pending("limited-invitation");
    jdbc.execute("truncate authentication_rate_limits");
    var body = "{\"identityKind\":\"SERVICE\",\"identifier\":\"limited-invitation\"}";
    for (int n = 0; n < 5; n++)
      assertEquals(
          202, http("POST", "/authentication/activation/request", body, null).statusCode());
    assertEquals(429, http("POST", "/authentication/activation/request", body, null).statusCode());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from authentication_notification_jobs", Integer.class));
  }

  @Autowired br.com.imarui.ima.identity.api.module.InitialIdentityProvisioning provisioning;

  @Test
  void rootBootstrapWithoutEmailFailsWithoutIdentityMarkerOrOutput() throws Exception {
    var dir = java.nio.file.Files.createTempDirectory("iiam-no-contact");
    var input = dir.resolve("input.json");
    var output = dir.resolve("output.json");
    java.nio.file.Files.writeString(
        input,
        "{\"kind\":\"SERVICE\",\"nickname\":\"root-no-contact\",\"name\":\"Initial operator\"}");
    var bootstrap =
        new br.com.imarui.ima.platform.bootstrap.AuthenticationBootstrap(
            provisioning,
            authentication,
            jdbc,
            json,
            transactions,
            clock,
            rootAuthorization,
            input.toString(),
            output.toString());
    try {
      assertThrows(
          RuntimeException.class,
          () -> bootstrap.run(new org.springframework.boot.DefaultApplicationArguments()));
      assertFalse(java.nio.file.Files.exists(output));
      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from ima_identities where nickname='root-no-contact'",
              Integer.class));
      assertEquals(
          0, jdbc.queryForObject("select count(*) from authentication_bootstrap", Integer.class));
    } finally {
      java.nio.file.Files.deleteIfExists(output);
      java.nio.file.Files.deleteIfExists(input);
      java.nio.file.Files.deleteIfExists(dir);
    }
  }

  @Test
  void nicknameIsRequiredAtDomainConstruction() {
    assertThrows(
        RuntimeException.class,
        () ->
            br.com.imarui.ima.identity.core.domain.model.identity.service.ServiceIdentity.create(
                IdentityId.generate(),
                IdentityEmail.create(
                    Email.from("contact@example.test"), EmailType.PRIMARY, Instant.now()),
                br.com.imarui.ima.identity.core.domain.model.identity.service.ServiceName.from(
                    "Service"),
                null,
                Instant.now(),
                null));
    assertThrows(
        RuntimeException.class,
        () ->
            IdentityApplication.create(
                IdentityApplicationId.generate(),
                Email.from("contact@example.test"),
                new ServiceIdentityApplicationDetails(
                    br.com.imarui.ima.identity.core.domain.model.identity.service.ServiceName.from(
                        "Service"),
                    null),
                Instant.now(),
                null));
  }

  @Test
  void databaseFailureDuringTokenRevocationRollsBackSessionLogout() {
    var i = active("logout-db-failure");
    var issued = authentication.login(IdentityKind.SERVICE, "logout-db-failure", PASSWORD);
    jdbc.execute(
        "create function fail_logout_token_update() returns trigger language plpgsql as $$ begin"
            + " raise exception 'simulated token write failure'; end $$");
    jdbc.execute(
        "create trigger fail_logout_token before update on authentication_refresh_tokens for each"
            + " row execute function fail_logout_token_update()");
    try {
      assertThrows(
          RuntimeException.class,
          () ->
              authentication.logout(
                  i.getId().value(), sid(issued.refreshToken()), issued.refreshToken()));
      assertEquals(
          "ACTIVE",
          jdbc.queryForObject(
              "select session_status from authentication_sessions where public_id=?",
              String.class,
              sid(issued.refreshToken())));
      assertEquals(
          "ACTIVE",
          jdbc.queryForObject(
              "select status from authentication_refresh_tokens where token_hash=?",
              String.class,
              hashes.hash(issued.refreshToken())));
    } finally {
      jdbc.execute("drop trigger fail_logout_token on authentication_refresh_tokens");
      jdbc.execute("drop function fail_logout_token_update()");
    }
    assertNotNull(authentication.refresh(issued.refreshToken()));
  }

  HttpResponse<String> logoutHttp(String access, String refresh) throws Exception {
    var csrf = http("GET", "/authentication/csrf", null, null);
    var proof = json.readTree(csrf.body()).path("token").asText();
    var cookies =
        csrf.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0]
            + (refresh == null ? "" : "; IIAM_REFRESH=" + refresh);
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/authentication/logout"))
            .header("Content-Type", "application/json")
            .header("Cookie", cookies)
            .header("X-XSRF-TOKEN", proof);
    if (access != null) request.header("Authorization", "Bearer " + access);
    return HttpClient.newHttpClient()
        .send(
            request.POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
            HttpResponse.BodyHandlers.ofString());
  }
}
