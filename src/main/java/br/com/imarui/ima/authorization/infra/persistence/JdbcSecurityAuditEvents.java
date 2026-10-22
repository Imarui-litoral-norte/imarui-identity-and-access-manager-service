package br.com.imarui.ima.authorization.infra.persistence;

import br.com.imarui.ima.authorization.api.module.SecurityAuditEvents;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@RequiredArgsConstructor
public class JdbcSecurityAuditEvents implements SecurityAuditEvents {
  private final JdbcTemplate jdbc;
  private final Clock clock;
  @Transactional(propagation=Propagation.REQUIRES_NEW)
  public void record(String event,UUID actor,String service,String resource,UUID scope,String reason,UUID correlation) {
    if(!event.matches("[A-Z0-9_]{1,60}") || !reason.matches("[A-Z0-9_]{1,80}"))
      throw new IllegalArgumentException("Security event codes must be fixed symbols.");
    jdbc.update("""
        insert into authorization_security_events(id,event,actor_identity_id,service_code,resource_id,scope_id,occurred_at,reason_code,correlation_id)
        values(?,?,?,?,?,?,?,?,?)
        """,UUID.randomUUID(),event,actor,service,resource,scope,Timestamp.from(clock.instant()),reason,
        correlation==null?UUID.randomUUID():correlation);
  }
}
