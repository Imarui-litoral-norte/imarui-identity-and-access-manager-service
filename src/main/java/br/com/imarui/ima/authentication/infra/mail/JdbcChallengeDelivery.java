package br.com.imarui.ima.authentication.infra.mail;

import br.com.imarui.ima.authentication.core.domain.model.AuthenticationChallenge.Purpose;
import br.com.imarui.ima.authentication.core.port.ChallengeDelivery;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JdbcChallengeDelivery implements ChallengeDelivery {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public void enqueue(UUID id, Purpose purpose) {
    jdbc.update(
        "insert into authentication_notification_jobs(id,identity_id,purpose,next_attempt_at)"
            + " values(?,?,?,?) on conflict do nothing",
        UUID.randomUUID(),
        id,
        purpose.name(),
        java.sql.Timestamp.from(clock.instant()));
  }
}
