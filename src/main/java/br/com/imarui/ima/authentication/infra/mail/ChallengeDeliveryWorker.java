package br.com.imarui.ima.authentication.infra.mail;

import br.com.imarui.ima.authentication.core.application.usecase.AuthenticationUseCases;
import br.com.imarui.ima.authentication.core.domain.model.AuthenticationChallenge.Purpose;
import br.com.imarui.ima.authentication.core.port.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ChallengeDeliveryWorker {
  private static final long[] BACKOFF_SECONDS = {30, 120, 600, 1800, 3600};
  private final JdbcTemplate jdbc;
  private final AuthenticationUseCases auth;
  private final IdentityAccess identities;
  private final ChallengeMail mail;
  private final Clock clock;
  private final TransactionTemplate tx;

  public ChallengeDeliveryWorker(
      JdbcTemplate jdbc,
      AuthenticationUseCases auth,
      IdentityAccess identities,
      ChallengeMail mail,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.jdbc = jdbc;
    this.auth = auth;
    this.identities = identities;
    this.mail = mail;
    this.clock = clock;
    this.tx = new TransactionTemplate(transactions);
  }

  private record Delivery(
      UUID job, UUID owner, String contact, String raw, Purpose purpose, int attempt) {
    @Override
    public String toString() {
      return "Delivery[REDACTED]";
    }
  }

  public boolean processOne() {
    // A dedicated session lock survives reservation commit and serializes the actual SMTP call.
    // A process/connection crash releases it; persisted PROCESSING and next_attempt_at recover
    // work.
    return Boolean.TRUE.equals(
        jdbc.execute(
            (ConnectionCallback<Boolean>)
                connection -> {
                  var candidates =
                      jdbc.queryForList(
                          "select id from authentication_notification_jobs where state in"
                              + " ('PENDING','PROCESSING') and next_attempt_at<=? order by"
                              + " next_attempt_at limit 20",
                          UUID.class,
                          Timestamp.from(clock.instant()));
                  for (UUID id : candidates) {
                    try (var lock =
                        connection.prepareStatement(
                            "select pg_try_advisory_lock(hashtextextended(?,390055))")) {
                      lock.setString(1, id.toString());
                      try (var rows = lock.executeQuery()) {
                        rows.next();
                        if (!rows.getBoolean(1)) continue;
                      }
                    }
                    try {
                      Delivery delivery = tx.execute(status -> reserve(id));
                      if (delivery == null) continue;
                      boolean sent = false;
                      try {
                        mail.send(delivery.contact(), delivery.raw(), delivery.purpose());
                        sent = true;
                      } catch (RuntimeException failure) {
                        /* SMTP exceptions may contain message secrets. Never persist/log them. */
                      }
                      final boolean success = sent;
                      tx.executeWithoutResult(
                          status -> {
                            String state =
                                success
                                    ? "DELIVERED"
                                    : delivery.attempt() >= 5 ? "FAILED" : "PENDING";
                            jdbc.update(
                                "update authentication_notification_jobs set"
                                    + " state=?,delivered_at=?,finished_at=? where id=?",
                                state,
                                success ? Timestamp.from(clock.instant()) : null,
                                success || delivery.attempt() >= 5
                                    ? Timestamp.from(clock.instant())
                                    : null,
                                id);
                            audit(
                                delivery.owner(),
                                success
                                    ? "NOTIFICATION_DELIVERED"
                                    : state.equals("FAILED")
                                        ? "NOTIFICATION_FAILED"
                                        : "NOTIFICATION_RETRY");
                          });
                      return true;
                    } finally {
                      try (var unlock =
                          connection.prepareStatement(
                              "select pg_advisory_unlock(hashtextextended(?,390055))")) {
                        unlock.setString(1, id.toString());
                        unlock.execute();
                      }
                    }
                  }
                  return false;
                }));
  }

  private Delivery reserve(UUID id) {
    var jobs =
        jdbc.queryForList(
            "select * from authentication_notification_jobs where id=? and state in"
                + " ('PENDING','PROCESSING') and next_attempt_at<=? for update",
            id,
            Timestamp.from(clock.instant()));
    if (jobs.isEmpty()) return null;
    var job = jobs.get(0);
    UUID owner = (UUID) job.get("identity_id");
    int attempts = ((Number) job.get("attempts")).intValue();
    // An interrupted fifth reservation is terminal, never a sixth SMTP attempt.
    if (attempts >= 5) {
      finish(id, owner, "FAILED");
      return null;
    }
    Purpose purpose = Purpose.valueOf((String) job.get("purpose"));
    var identity = identities.lock(owner).orElse(null);
    if (identity == null
        || identity.email() == null
        || (purpose == Purpose.ACCOUNT_ACTIVATION
            ? identity.status()
                != br.com.imarui.ima.identity.core.domain.enums.identity.IdentityStatus.PENDING
            : !identity.active())) {
      finish(id, owner, "CANCELLED");
      return null;
    }
    jdbc.update(
        "update authentication_notification_jobs set"
            + " attempts=attempts+1,state='PROCESSING',next_attempt_at=? where id=?",
        Timestamp.from(clock.instant().plusSeconds(BACKOFF_SECONDS[attempts])),
        id);
    return new Delivery(
        id, owner, identity.email(), auth.issueChallenge(owner, purpose), purpose, attempts + 1);
  }

  private void finish(UUID id, UUID owner, String state) {
    jdbc.update(
        "update authentication_notification_jobs set state=?,finished_at=? where id=?",
        state,
        Timestamp.from(clock.instant()),
        id);
    audit(owner, "NOTIFICATION_" + state);
  }

  private void audit(UUID owner, String event) {
    jdbc.update(
        "insert into authentication_audit(identity_id,event,occurred_at) values(?,?,?)",
        owner,
        event,
        Timestamp.from(clock.instant()));
  }
}
