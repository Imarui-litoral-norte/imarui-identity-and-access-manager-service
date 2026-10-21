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
    if (raw == null || raw.isBlank()) return;
    var t = store.token(hash(raw), false).orElse(null);
    if (t == null) return;
    var owner = t.getSession().getIdentityId();
    if (owner == null) return;
    identities.lock(owner);
    store.credential(owner);
    var s = store.session(t.getSession().getPublicId(), true).orElse(null);
    if (s == null) return;
    s.logout(clock.instant());
    store.save(s);
    store.revokeTokens(s.getId());
    store.audit(owner, "LOGOUT", clock.instant());
  }

  @Transactional
  public void endSession(UUID owner, UUID sid) {
    identities.lock(owner).orElseThrow(AuthenticationFailure::new);
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
    store.replaceChallenge(
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
        || (purpose == Purpose.ACCOUNT_ACTIVATION
            ? identity.status() != IdentityStatus.PENDING
            : !identity.active())) throw new AuthenticationFailure();
    if (c == null) c = new Credential(identity.id(), null, 0, null, true, now, now);
    store.save(c.password(passwords.hash(password), now));
    store.consume(challenge.id(), now);
    store.revoke(identity.id(), now);
    if (purpose == Purpose.ACCOUNT_ACTIVATION) identities.activate(identity.id());
    store.audit(
        identity.id(),
        purpose == Purpose.ACCOUNT_ACTIVATION ? "FIRST_ACCESS_COMPLETED" : "PASSWORD_RESET",
        now);
  }

  @Transactional
  public void changePassword(UUID owner, String current, String next) {
    Credential.validatePassword(next);
    var i = identities.lock(owner).orElseThrow(AuthenticationFailure::new);
    var c = store.credential(owner).orElseThrow(AuthenticationFailure::new);
    var now = clock.instant();
    if (!i.active() || !c.allowed(now) || !passwords.matches(current, c.passwordHash()))
      throw new AuthenticationFailure();
    store.save(c.password(passwords.hash(next), now));
    store.revoke(owner, now);
    store.audit(owner, "PASSWORD_CHANGED", now);
  }

  private String hash(String raw) {
    if (raw == null || raw.length() < 32 || raw.length() > 256) throw new AuthenticationFailure();
    return hashes.hash(raw);
  }
}
