package br.com.imarui.ima.authorization.infra.persistence;

import br.com.imarui.ima.authorization.core.domain.model.RootTransfer;
import br.com.imarui.ima.authorization.core.repository.RootRecoveryRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@RequiredArgsConstructor
@Transactional(propagation=Propagation.MANDATORY)
public class JdbcRootRecoveryRepository implements RootRecoveryRepository {
  private final JdbcTemplate jdbc;
  public void create(RootTransfer t) {
    if(jdbc.queryForObject("select count(*) from authorization_root_transfers where initiator_id=? and confirmed_at is null and expires_at>?",
        Integer.class,t.initiatorId(),Timestamp.from(t.createdAt()))>=3) throw new IllegalStateException("At most three pending recovery approvals.");
    jdbc.update("insert into authorization_root_transfers(id,initiator_id,initiator_session_id,recipient_id,scope_id,reason,correlation_id,created_at,expires_at) values(?,?,?,?,?,?,?,?,?)",
        t.id(),t.initiatorId(),t.initiatorSessionId(),t.recipientId(),t.scopeId(),t.reason(),t.correlationId(),Timestamp.from(t.createdAt()),Timestamp.from(t.expiresAt()));
  }
  public Optional<RootTransfer> lock(UUID id) {
    return jdbc.query("select * from authorization_root_transfers where id=? for update",(r,n) ->
      new RootTransfer(id,r.getObject("initiator_id",UUID.class),r.getObject("initiator_session_id",UUID.class),
        r.getObject("recipient_id",UUID.class),r.getObject("scope_id",UUID.class),r.getString("reason"),
        r.getObject("correlation_id",UUID.class),r.getTimestamp("created_at").toInstant(),
        r.getTimestamp("expires_at").toInstant(),r.getTimestamp("confirmed_at")==null?null:r.getTimestamp("confirmed_at").toInstant()),id).stream().findFirst();
  }
  public void confirm(UUID id,Instant now) {
    if(jdbc.update("update authorization_root_transfers set confirmed_at=? where id=? and confirmed_at is null and expires_at>?",
        Timestamp.from(now),id,Timestamp.from(now))!=1) throw new IllegalStateException("Recovery approval expired or already consumed.");
  }
}
