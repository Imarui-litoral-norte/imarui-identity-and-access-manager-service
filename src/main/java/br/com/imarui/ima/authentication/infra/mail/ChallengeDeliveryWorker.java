package br.com.imarui.ima.authentication.infra.mail;

import br.com.imarui.ima.authentication.core.application.usecase.AuthenticationUseCases;
import br.com.imarui.ima.authentication.core.domain.model.AuthenticationChallenge.Purpose;
import br.com.imarui.ima.authentication.core.port.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ChallengeDeliveryWorker {
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

  private record Delivery(UUID job, String contact, String raw, Purpose purpose) {
    @Override
    public String toString() {
      return "Delivery[REDACTED]";
    }
  }

  public boolean processOne() {
    Delivery delivery =
        tx.execute(
            status -> {
              var rows =
                  jdbc.query(
                      "select * from authentication_notification_jobs where delivered_at is null"
                          + " and next_attempt_at<=? and attempts<10 order by next_attempt_at limit"
                          + " 1 for update skip locked",
                      (r, n) ->
                          Map.of(
                              "id",
                              r.getObject("id", UUID.class),
                              "identity",
                              r.getObject("identity_id", UUID.class),
                              "purpose",
                              r.getString("purpose")),
                      java.sql.Timestamp.from(clock.instant()));
              if (rows.isEmpty()) return null;
              var job = rows.get(0);
              var id = (UUID) job.get("id");
              var owner = (UUID) job.get("identity");
              var purpose = Purpose.valueOf((String) job.get("purpose"));
              var i = identities.lock(owner).orElse(null);
              if (i == null
                  || i.email() == null
                  || (purpose == Purpose.ACCOUNT_ACTIVATION
                      ? i.status()
                          != br.com.imarui.ima.identity.core.domain.enums.identity.IdentityStatus
                              .PENDING
                      : !i.active())) {
                jdbc.update(
                    "update authentication_notification_jobs set delivered_at=? where id=?",
                    java.sql.Timestamp.from(clock.instant()),
                    id);
                return null;
              }
              jdbc.update(
                  "update authentication_notification_jobs set"
                      + " attempts=attempts+1,next_attempt_at=? where id=?",
                  java.sql.Timestamp.from(clock.instant().plusSeconds(300)),
                  id);
              return new Delivery(id, i.email(), auth.issueChallenge(owner, purpose), purpose);
            });
    if (delivery == null) return false;
    try {
      mail.send(delivery.contact(), delivery.raw(), delivery.purpose());
      jdbc.update(
          "update authentication_notification_jobs set delivered_at=? where id=?",
          java.sql.Timestamp.from(clock.instant()),
          delivery.job());
    } catch (RuntimeException failure) {
      /* Metadata remains retryable. No exception text: SMTP errors may contain secret message data. */
    }
    return true;
  }
}
