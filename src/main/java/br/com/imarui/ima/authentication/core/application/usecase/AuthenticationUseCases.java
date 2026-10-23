package br.com.imarui.ima.authentication.core.application.usecase;

import br.com.imarui.ima.authentication.core.application.exception.AuthenticationFailure;
import br.com.imarui.ima.authentication.core.domain.model.*;
import br.com.imarui.ima.authentication.core.domain.model.AuthenticationChallenge.Purpose;
import br.com.imarui.ima.authentication.core.port.*;
import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationUseCases {
  private final AuthenticationStore store;
  private final IdentityAccess identities;
  private final PasswordHasher passwords;
  private final RefreshTokenGenerator random;
  private final RefreshTokenHashService hashes;
  private final AccessTokenIssuer jwt;
  private final ChallengeDelivery delivery;
  private final ApplicationTimeProperties timeProperties;
  private final Clock clock;
  private final AuthenticationOperationAccess access;
  // Startup-only dummy hash uses the configured BCrypt cost, matching real account verification.
  private String dummyHash;

  @jakarta.annotation.PostConstruct
  void initializeDummyHash() {
    dummyHash = passwords.hash(random.generate().substring(0, 32));
  }

  public record Issued(String accessToken, String refreshToken, Instant sessionExpiresAt) {
    @Override
    public String toString() {
      return "Issued[REDACTED]";
    }
  }

  public record OwnSession(UUID sid, Instant createdAt, Instant expiresAt, String status) {}

  @Transactional(noRollbackFor = AuthenticationFailure.class)
  public Issued login(IdentityKind kind, String identifier, String password) {
    var candidate = identities.resolve(kind, identifier).orElse(null);
    if (candidate == null) {
      passwords.matches(password, dummyHash);
      store.audit(null, "LOGIN_FAILED", clock.instant());
      throw new AuthenticationFailure();
    }
    var identity = identities.lock(candidate.id()).orElseThrow(AuthenticationFailure::new);
    if (identities.resolve(kind, identifier).filter(current -> current.id().equals(identity.id())).isEmpty())
      throw new AuthenticationFailure();
    var credential = store.credential(identity.id()).orElse(null);
    var now = clock.instant();
    boolean matches =
        passwords.matches(password, credential == null ? dummyHash : credential.passwordHash());
    if (credential == null
        || !identity.active()
        || identity.nickname() == null
        || !credential.allowed(now)
        || !matches) {
      if (credential != null && identity.active() && credential.allowed(now) && !matches)
        store.save(credential.failed(now));
      store.audit(identity.id(), "LOGIN_FAILED", now);
      throw new AuthenticationFailure();
    }
    var session = store.createSession(identity.id(), now);
    var issued = issue(session, now);
    store.save(credential.successful(now));
    store.audit(identity.id(), "LOGIN_SUCCESS", now);
    return issued;
  }

  private Issued issue(Session session, Instant now) {
    String raw = random.generate();
    var token =
        RefreshToken.create(
            session, hashes.hash(raw), Duration.between(now, session.getExpiresAt()), now);
    store.save(token);
    return new Issued(
        jwt.issue(session.getIdentityId(), session.getPublicId(), now),
        raw,
        session.getExpiresAt());
  }

  @Transactional(noRollbackFor = AuthenticationFailure.class)
  public Issued refresh(String raw) {
    String hash = hash(raw);
    var initial = store.token(hash, false).orElseThrow(AuthenticationFailure::new);
    var owner = initial.getSession().getIdentityId();
    if (owner == null) throw new AuthenticationFailure();
    var identity = identities.lock(owner).orElseThrow(AuthenticationFailure::new);
    var c = store.credential(owner).orElseThrow(AuthenticationFailure::new);
    var s =
        store
            .session(initial.getSession().getPublicId(), true)
            .orElseThrow(AuthenticationFailure::new);
    var old = store.token(hash, true).orElseThrow(AuthenticationFailure::new);
    var now = clock.instant();
    if (old.isRotated()) {
      s.revokeAt(now);
      store.save(s);
      store.revokeTokens(s.getId());
      store.audit(owner, "REFRESH_REPLAY", now);
      throw new AuthenticationFailure();
    }
    if (!identity.active()
        || !c.allowed(now)
        || !s.isValid(now)
        || !old.isValid(now)
        || !identities.valid(owner, s.getCreatedAt(), null)) throw new AuthenticationFailure();
    String next = random.generate();
    var successor =
        store.save(
            RefreshToken.create(
                s, hashes.hash(next), Duration.between(now, s.getExpiresAt()), now));
    old.rotate(successor.getId(), now);
    store.save(old);
    store.audit(owner, "REFRESH_ROTATED", now);
    return new Issued(jwt.issue(owner, s.getPublicId(), now), next, s.getExpiresAt());
  }

  @Transactional
  public void logout(String raw) {
    logout(null, null, raw);
  }

  @Transactional(noRollbackFor = AuthenticationFailure.class)
  public void logout(UUID actor, UUID sid, String raw) {
    boolean cookie = raw != null;
    RefreshToken token = null;
    if (cookie) {
      try {
        token = store.token(hash(raw), false).orElse(null);
      } catch (AuthenticationFailure invalid) {
        /* Compare before effects below. */
      }
    }
    if (actor != null
        && cookie
        && (token == null
            || !actor.equals(token.getSession().getIdentityId())
            || !sid.equals(token.getSession().getPublicId()))) {
      store.audit(actor, null, "LOGOUT_MISMATCH", "DENIED", null, clock.instant());
      throw new AuthenticationFailure();
    }
    UUID owner = actor != null ? actor : token == null ? null : token.getSession().getIdentityId();
    UUID selected = sid != null ? sid : token == null ? null : token.getSession().getPublicId();
    if (owner == null || selected == null) return;
    identities.lock(owner);
    store.credential(owner);
    var session =
        store
            .session(selected, true)
            .filter(s -> owner.equals(s.getIdentityId()))
            .orElseThrow(AuthenticationFailure::new);
    session.logout(clock.instant());
    store.save(session);
    store.revokeTokens(session.getId());
    store.audit(owner, "LOGOUT", clock.instant());
  }

  @Transactional
  public void endSession(UUID owner, UUID sid) {
    identities.lock(owner).orElseThrow(AuthenticationFailure::new);
    access.requireIdentity(owner);
    store.credential(owner);
    var s =
        store
            .session(sid, true)
            .filter(v -> owner.equals(v.getIdentityId()))
            .orElseThrow(AuthenticationFailure::new);
    s.logout(clock.instant());
    store.save(s);
    store.revokeTokens(s.getId());
    store.audit(owner, "SESSION_LOGOUT", clock.instant());
  }

  @Transactional(readOnly = true)
  public List<OwnSession> sessions(UUID owner) {
    access.requireIdentity(owner);
    return store.sessions(owner).stream()
        .map(
            s ->
                new OwnSession(
                    s.getPublicId(), s.getCreatedAt(), s.getExpiresAt(), s.getStatus().name()))
        .toList();
  }

  @Transactional
  public void request(IdentityKind kind, String identifier, Purpose purpose) {
    identities
        .resolve(kind, identifier)
        .ifPresent(
            i -> {
              if (i.email() != null
                  && ((purpose == Purpose.ACCOUNT_ACTIVATION
                          && i.status() == IdentityStatus.PENDING)
                      || (purpose == Purpose.PASSWORD_RESET && i.active())))
                delivery.enqueue(i.id(), purpose);
            });
  }

  @Transactional
  public String issueChallenge(UUID owner, Purpose purpose) {
    var i = identities.lock(owner).orElseThrow(AuthenticationFailure::new);
    if (i.nickname() == null
        || (purpose == Purpose.ACCOUNT_ACTIVATION
            ? i.status() != IdentityStatus.PENDING
            : !i.active())) throw new AuthenticationFailure();
    var now = clock.instant();
    store.credential(owner);
    String raw = random.generate();
    store.addChallenge(
        new AuthenticationChallenge(
            UUID.randomUUID(),
            owner,
            purpose,
            hashes.hash(raw),
            now,
            now.plus(
                purpose == Purpose.ACCOUNT_ACTIVATION
                    ? timeProperties.activationInvitationTtl()
                    : timeProperties.passwordRecoveryRequestTtl()),
            null));
    store.audit(owner, "CHALLENGE_ISSUED", now);
    return raw;
  }

  @Transactional(noRollbackFor = AuthenticationFailure.class)
  public String administrativeInvitation(UUID actor, UUID target, String origin) {
    try {
      identities.lock(target).orElseThrow(AuthenticationFailure::new);
      access.requireIdentity(actor);
      access.requirePlatform("AUTHENTICATION_INVITATION_CREATE");
      String raw = issueChallenge(target, Purpose.ACCOUNT_ACTIVATION);
      store.audit(actor, target, "ADMIN_INVITATION", "SUCCESS", origin, clock.instant());
      return raw;
    } catch (AuthenticationFailure failure) {
      store.audit(actor, target, "ADMIN_INVITATION", "DENIED", origin, clock.instant());
      throw failure;
    }
  }

  @Transactional
  public void complete(String raw, String password, Purpose purpose) {
    Credential.validatePassword(password);
    String hash = hash(raw);
    var initial = store.challenge(hash, false).orElseThrow(AuthenticationFailure::new);
    var identity = identities.lock(initial.identityId()).orElseThrow(AuthenticationFailure::new);
    var c = store.credential(identity.id()).orElse(null);
    var challenge = store.challenge(hash, true).orElseThrow(AuthenticationFailure::new);
    var now = clock.instant();
    if (!challenge.valid(purpose, now)
        || (purpose == Purpose.PASSWORD_RESET
            && !identities.valid(identity.id(), challenge.createdAt(), null))
        || (purpose == Purpose.ACCOUNT_ACTIVATION
            ? identity.status() != IdentityStatus.PENDING
            : !identity.active())) throw new AuthenticationFailure();
    if (c == null) c = new Credential(identity.id(), null, 0, null, true, now, now);
    store.save(c.password(passwords.hash(password), now));
    store.consumePurpose(identity.id(), purpose, now);
    store.revokeSessions(identity.id(), now);
    if (purpose == Purpose.ACCOUNT_ACTIVATION) identities.activate(identity.id());
    store.audit(
        identity.id(),
        purpose == Purpose.ACCOUNT_ACTIVATION ? "FIRST_ACCESS_COMPLETED" : "PASSWORD_RESET",
        now);
  }

  @Transactional(noRollbackFor = AuthenticationFailure.class)
  public void changePassword(UUID owner, String current, String next) {
    Credential.validatePassword(next);
    var i = identities.lock(owner).orElseThrow(AuthenticationFailure::new);
    access.requireIdentity(owner);
    var c = store.credential(owner).orElseThrow(AuthenticationFailure::new);
    var now = clock.instant();
    boolean matches = passwords.matches(current, c.passwordHash());
    if (!i.active() || !c.allowed(now) || !matches) {
      if (i.active() && c.allowed(now) && !matches) store.save(c.failed(now));
      store.audit(owner, "PASSWORD_CHANGE_FAILED", now);
      throw new AuthenticationFailure();
    }
    store.save(c.password(passwords.hash(next), now));
    store.revoke(owner, now);
    store.audit(owner, "PASSWORD_CHANGED", now);
  }

  private String hash(String raw) {
    if (raw == null || raw.length() < 32 || raw.length() > 256) throw new AuthenticationFailure();
    return hashes.hash(raw);
  }
}
