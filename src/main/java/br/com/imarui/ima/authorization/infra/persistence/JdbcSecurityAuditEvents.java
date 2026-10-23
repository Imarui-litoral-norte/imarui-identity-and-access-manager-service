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
  private final tools.jackson.databind.json.JsonMapper json;
  private final io.micrometer.core.instrument.MeterRegistry metrics;
  private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(JdbcSecurityAuditEvents.class);
  @Transactional(propagation=Propagation.MANDATORY)
  public void decision(UUID id,UUID actor,String service,String type,String resource,UUID scope,String permission,String reason,UUID correlation) {
    try {
    jdbc.update("""
        insert into authorization_security_events(id,event,actor_identity_id,service_code,resource_id,scope_id,occurred_at,reason_code,correlation_id,details)
        values(?,'PDP_DECISION',?,?,?,?,?,?,?,?::jsonb)
        """,id,actor,service,resource,scope,Timestamp.from(clock.instant()),reason,correlation,
        json.writeValueAsString(java.util.Map.of("permission",permission,"resourceType",type)));
    } catch(org.springframework.dao.DataAccessException failure) { failed("DECISION",correlation); throw failure; }
  }
  @Transactional(propagation=Propagation.REQUIRES_NEW)
  public void record(String event,UUID actor,String service,String resource,UUID scope,String reason,UUID correlation) {
    append(event,actor,service,resource,scope,reason,correlation);
  }
  @Transactional(propagation=Propagation.MANDATORY)
  public void recordInTransaction(String event,UUID actor,String service,String resource,UUID scope,String reason,UUID correlation) {
    append(event,actor,service,resource,scope,reason,correlation);
  }
  private void append(String event,UUID actor,String service,String resource,UUID scope,String reason,UUID correlation) {
    if(!event.matches("[A-Z0-9_]{1,60}") || !reason.matches("[A-Z0-9_]{1,80}"))
      throw new IllegalArgumentException("Security event codes must be fixed symbols.");
    try { jdbc.update("""
        insert into authorization_security_events(id,event,actor_identity_id,service_code,resource_id,scope_id,occurred_at,reason_code,correlation_id)
        values(?,?,?,?,?,?,?,?,?)
        """,UUID.randomUUID(),event,actor,service,resource,scope,Timestamp.from(clock.instant()),reason,
        correlation==null?UUID.randomUUID():correlation);
    } catch(org.springframework.dao.DataAccessException failure) { failed("SECURITY",correlation); throw failure; }
  }
  private void failed(String journal,UUID correlation) {
    metrics.counter("iiam.security.audit.failures","journal",journal).increment();
    log.error("security_audit_failure journal={} correlationId={}",journal,correlation);
  }
}
