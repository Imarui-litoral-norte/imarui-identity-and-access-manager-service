package br.com.imarui.ima.identity.infra.integration;

import br.com.imarui.ima.identity.api.module.RevocationRequests;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class RevocationRequestsAdapter implements RevocationRequests {
  private final JdbcTemplate jdbc;

  public List<Request> pending() {
    return jdbc.query(
        "select * from ima_access_revocation_requests where processed_at is null order by"
            + " requested_at,id limit 20 for update skip locked",
        (r, n) ->
            new Request(
                r.getObject("id", UUID.class),
                r.getString("scope"),
                r.getObject("subject_id", UUID.class),
                r.getTimestamp("requested_at").toInstant()));
  }

  public void processed(UUID id, Instant now) {
    jdbc.update(
        "update ima_access_revocation_requests set processed_at=? where id=? and processed_at is"
            + " null",
        java.sql.Timestamp.from(now),
        id);
  }
}
