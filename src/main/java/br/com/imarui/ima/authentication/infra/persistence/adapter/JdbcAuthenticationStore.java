package br.com.imarui.ima.authentication.infra.persistence.adapter;

import br.com.imarui.ima.authentication.core.domain.model.*;
import br.com.imarui.ima.authentication.core.port.AuthenticationStore;
import br.com.imarui.ima.authentication.core.repository.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcAuthenticationStore implements AuthenticationStore {
  private final JdbcTemplate jdbc;
  private final SessionRepository sessions;
  private final RefreshTokenRepository tokens;

  private static Timestamp ts(Instant i) {
    return i == null ? null : Timestamp.from(i);
  }

  private static Instant time(ResultSet r, String c) throws SQLException {
    var t = r.getTimestamp(c);
    return t == null ? null : t.toInstant();
  }

  public Optional<Credential> credential(UUID id) {
    return jdbc
        .query(
            "select * from authentication_credentials where identity_id=? for update",
            (r, n) ->
                new Credential(
                    id,
                    r.getString("password_hash"),
                    r.getInt("failures"),
                    time(r, "locked_until"),
                    r.getBoolean("enabled"),
                    time(r, "created_at"),
                    time(r, "updated_at")),
            id)
        .stream()
        .findFirst();
  }

  public void save(Credential c) {
    jdbc.update(
        "insert into"
            + " authentication_credentials(identity_id,password_hash,failures,locked_until,enabled,created_at,updated_at)"
            + " values(?,?,?,?,?,?,?) on conflict(identity_id) do update set"
            + " password_hash=excluded.password_hash,failures=excluded.failures,locked_until=excluded.locked_until,enabled=excluded.enabled,updated_at=excluded.updated_at",
        c.identityId(),
        c.passwordHash(),
        c.failures(),
        ts(c.lockedUntil()),
        c.enabled(),
        ts(c.createdAt()),
        ts(c.updatedAt()));
  }

  public Optional<AuthenticationChallenge> challenge(String hash, boolean lock) {
    return jdbc
        .query(
            "select * from authentication_challenges where token_hash=?"
                + (lock ? " for update" : ""),
            (r, n) ->
                new AuthenticationChallenge(
                    r.getObject("id", UUID.class),
                    r.getObject("identity_id", UUID.class),
                    AuthenticationChallenge.Purpose.valueOf(r.getString("purpose")),
                    r.getString("token_hash"),
                    time(r, "created_at"),
                    time(r, "expires_at"),
                    time(r, "consumed_at")),
            hash)
        .stream()
        .findFirst();
  }

  public void replaceChallenge(AuthenticationChallenge c) {
    jdbc.update(
        "update authentication_challenges set consumed_at=? where identity_id=? and purpose=? and"
            + " consumed_at is null",
        ts(c.createdAt()),
        c.identityId(),
        c.purpose().name());
    jdbc.update(
        "insert into authentication_challenges values(?,?,?,?,?,?,?)",
        c.id(),
        c.identityId(),
        c.purpose().name(),
        c.tokenHash(),
        ts(c.createdAt()),
        ts(c.expiresAt()),
        null);
  }

  public void consume(UUID id, Instant now) {
    jdbc.update(
        "update authentication_challenges set consumed_at=? where id=? and consumed_at is null",
        ts(now),
        id);
  }

  public Session createSession(UUID id, Instant now) {
    return sessions.save(Session.createForIdentity(id, now));
  }

  public Optional<Session> session(UUID sid, boolean lock) {
    return jdbc
        .queryForList("select id from authentication_sessions where public_id=?", Long.class, sid)
        .stream()
        .findFirst()
        .flatMap(id -> lock ? sessions.findByIdForUpdate(id) : sessions.findById(id));
  }

  public void save(Session s) {
    sessions.save(s);
  }

  public Optional<RefreshToken> token(String hash, boolean lock) {
    if (lock) return tokens.findByTokenHashForUpdate(hash);
    // Discovery must not populate the persistence context before waiting on the owner lock.
    // Otherwise a later SELECT FOR UPDATE may return an already-managed, stale token.
    return jdbc
        .query(
            "select * from authentication_refresh_tokens where token_hash=?",
            (r, n) -> {
              Long sid = r.getLong("session_id");
              var session =
                  jdbc.queryForObject(
                      "select * from authentication_sessions where id=?",
                      (sr, sn) ->
                          Session.reconstitute(
                                  sr.getLong("id"),
                                  time(sr, "created_at"),
                                  time(sr, "expires_at"),
                                  time(sr, "logged_out_at"),
                                  br.com.imarui.ima.authentication.core.domain.enums.SessionStatus
                                      .valueOf(sr.getString("session_status")))
                              .authenticationContext(
                                  sr.getObject("identity_id", UUID.class),
                                  sr.getObject("public_id", UUID.class),
                                  time(sr, "revoked_at")),
                      sid);
              return RefreshToken.reconstitute(
                  r.getLong("id"),
                  session,
                  r.getString("token_hash"),
                  time(r, "created_at"),
                  time(r, "expires_at"),
                  br.com.imarui.ima.authentication.core.domain.enums.RefreshTokenStatus.valueOf(
                      r.getString("status")),
                  r.getObject("replaced_by_token_id", Long.class));
            },
            hash)
        .stream()
        .findFirst();
  }

  public RefreshToken save(RefreshToken token) {
    return tokens.save(token);
  }

  public void revokeTokens(long session) {
    tokens.revokeActiveBySessionId(session);
  }

  public void revoke(UUID id, Instant cutoff) {
    var ids =
        jdbc.queryForList(
            "select id from authentication_sessions where identity_id=? and created_at<=? order by"
                + " id for update",
            Long.class,
            id,
            ts(cutoff));
    for (var sid : ids) {
      var s = sessions.findByIdForUpdate(sid).orElseThrow();
      s.revokeAt(cutoff);
      sessions.save(s);
      tokens.revokeActiveBySessionId(sid);
    }
    jdbc.update(
        "update authentication_challenges set consumed_at=? where identity_id=? and consumed_at is"
            + " null and created_at<=?",
        ts(cutoff),
        id,
        ts(cutoff));
  }

  public List<Session> sessions(UUID owner) {
    return jdbc
        .queryForList(
            "select id from authentication_sessions where identity_id=? order by created_at desc"
                + " limit 100",
            Long.class,
            owner)
        .stream()
        .map(id -> sessions.findById(id).orElseThrow())
        .toList();
  }

  public void audit(UUID id, String event, Instant now) {
    jdbc.update(
        "insert into authentication_audit(identity_id,event,occurred_at) values(?,?,?)",
        id,
        event,
        ts(now));
  }
}
