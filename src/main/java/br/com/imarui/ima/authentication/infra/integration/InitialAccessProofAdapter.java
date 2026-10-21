package br.com.imarui.ima.authentication.infra.integration;

import br.com.imarui.ima.identity.core.application.port.InitialAccessProof;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InitialAccessProofAdapter implements InitialAccessProof {
  private final JdbcTemplate jdbc;

  public boolean established(UUID id) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from authentication_credentials c join"
                + " authentication_challenges h on h.identity_id=c.identity_id where"
                + " c.identity_id=? and c.password_hash is not null and c.enabled and"
                + " h.purpose='ACCOUNT_ACTIVATION' and h.consumed_at is not null)",
            Boolean.class,
            id));
  }
}
