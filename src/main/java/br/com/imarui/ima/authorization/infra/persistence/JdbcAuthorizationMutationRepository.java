package br.com.imarui.ima.authorization.infra.persistence;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.repository.AuthorizationMutationRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import org.springframework.dao.OptimisticLockingFailureException;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
@Transactional(propagation=Propagation.MANDATORY)
public class JdbcAuthorizationMutationRepository implements AuthorizationMutationRepository {
  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  private final io.micrometer.core.instrument.MeterRegistry metrics;
  public void lock() {
    var sample=io.micrometer.core.instrument.Timer.start(metrics); String outcome="ERROR";
    try { jdbc.query("select pg_advisory_xact_lock(810082)",r -> {}); outcome="ACQUIRED"; }
    finally { sample.stop(metrics.timer("iiam.authorization.mutation.lock","outcome",outcome)); }
  }
  public Role saveRole(Role role) {
    if (role.id()==null) {
      long id=jdbc.queryForObject("insert into authorization_roles(name,ownership,owner_scope_id,owner_tenant_id,status,created_at,updated_at,version) values(?,?,?,?,?,?,?,0) returning id",
          Long.class,role.name(),role.ownership().name(),role.ownerScopeId(),role.ownerTenantId(),role.status().name(),Timestamp.from(role.createdAt()),Timestamp.from(role.updatedAt()));
      return new Role(id,role.name(),role.ownership(),role.ownerScopeId(),role.ownerTenantId(),role.status(),role.createdAt(),role.updatedAt(),0);
    }
    changed(jdbc.update("update authorization_roles set name=?,status=?,updated_at=?,version=? where id=? and version=?",
        role.name(),role.status().name(),Timestamp.from(role.updatedAt()),role.version(),role.id(),role.version()-1));
    return role;
  }
  public void deleteRole(Role role,UUID actor,PolicyChangeReason reason,Instant now) {
    jdbc.update("""
        insert into authorization_roles__history(id,original_role_id,deleted_at,actor_identity_id,reason,correlation_id,
          role_state,permission_state,deny_state)
        select ?,r.id,?,?,?,?,to_jsonb(r),
          coalesce((select jsonb_agg(to_jsonb(p)) from authorization_role_permissions p where p.role_id=r.id),'[]'::jsonb),
          coalesce((select jsonb_agg(to_jsonb(d)) from authorization_denies d where d.role_id=r.id),'[]'::jsonb)
        from authorization_roles r where r.id=? and r.version=?
        """,UUID.randomUUID(),Timestamp.from(now),actor,reason.reason(),reason.correlationId(),role.id(),role.version());
    jdbc.update("delete from authorization_denies where role_id=?",role.id());
    jdbc.update("delete from authorization_role_permissions where role_id=?",role.id());
    changed(jdbc.update("delete from authorization_roles where id=? and version=?",role.id(),role.version()));
  }
  public void deleteScope(AuthorizationScope scope,UUID actor,PolicyChangeReason reason,Instant now) {
    jdbc.update("insert into authorization_scopes__history(id,original_scope_id,deleted_at,actor_identity_id,reason,correlation_id,scope_state) values(?,?,?,?,?,?,?::jsonb)",
        UUID.randomUUID(),scope.id(),Timestamp.from(now),actor,reason.reason(),reason.correlationId(),json.writeValueAsString(scope));
    changed(jdbc.update("delete from authorization_scopes where id=? and version=?",scope.id(),scope.version()));
  }
  public void replacePermissions(long role,Set<String> codes) {
    var existing=jdbc.queryForList("select p.code from authorization_role_permissions rp join authorization_permissions p on p.id=rp.permission_id where rp.role_id=?",String.class,role);
    for (var code:existing) if (!codes.contains(code)) jdbc.update("delete from authorization_role_permissions where role_id=? and permission_id=(select id from authorization_permissions where code=?)",role,code);
    for (var code:codes) {
      int count=jdbc.update("insert into authorization_role_permissions(role_id,permission_id) select ?,id from authorization_permissions where code=? on conflict do nothing",role,code);
      if (count==0 && !existing.contains(code)) throw new IllegalArgumentException("Unknown Permission.");
    }
  }
  public void saveAssignment(RoleAssignment a) {
    if (a.version()==0) jdbc.update("insert into authorization_identity_roles(id,identity_id,role_id,authorization_scope_id,active,created_at,updated_at,version) values(?,?,?,?,?,?,?,0)",
        a.id(),a.identityId(),a.roleId(),a.scopeId(),a.active(),Timestamp.from(a.createdAt()),Timestamp.from(a.updatedAt()));
    else changed(jdbc.update("update authorization_identity_roles set active=?,updated_at=?,version=? where id=? and version=?",
        a.active(),Timestamp.from(a.updatedAt()),a.version(),a.id(),a.version()-1));
  }
  public void saveDeny(DenyRule d) {
    if (d.version()==0) jdbc.update("insert into authorization_denies(id,permission_code,scope_id,identity_id,role_id,active,reason,created_at,updated_at,version) values(?,?,?,?,?,?,?,?,?,0)",
        d.id(),d.permission(),d.scopeId(),d.identityId(),d.roleId(),d.active(),d.reason(),Timestamp.from(d.createdAt()),Timestamp.from(d.updatedAt()));
    else changed(jdbc.update("update authorization_denies set active=?,reason=?,updated_at=?,version=? where id=? and version=?",
        d.active(),d.reason(),Timestamp.from(d.updatedAt()),d.version(),d.id(),d.version()-1));
  }
  public void saveDelegation(PermissionDelegation d) {
    if (d.version()==0) jdbc.update("insert into authorization_permission_delegations(id,identity_id,permission_code,scope_id,redelegable,active,reason,created_at,updated_at,version) values(?,?,?,?,?,?,?,?,?,0)",
        d.id(),d.identityId(),d.permission(),d.scopeId(),d.redelegable(),d.active(),d.reason(),Timestamp.from(d.createdAt()),Timestamp.from(d.updatedAt()));
    else changed(jdbc.update("update authorization_permission_delegations set active=?,redelegable=?,reason=?,updated_at=?,version=? where id=? and version=?",
        d.active(),d.redelegable(),d.reason(),Timestamp.from(d.updatedAt()),d.version(),d.id(),d.version()-1));
  }
  public void audit(UUID actor,String operation,String type,String resource,UUID scope,
      PolicyChangeReason reason,Object before,Object after,Instant now) {
    metrics.counter("iiam.authorization.mutation.attempts","action",operation).increment();
    try {
    jdbc.update("insert into authorization_audit(id,actor_identity_id,service_code,action,resource_type,resource_id,scope_id,occurred_at,reason,correlation_id,before_state,after_state) values(?,?,'iiam',?,?,?,?,?,?,?,?::jsonb,?::jsonb)",
        UUID.randomUUID(),actor,operation,type,resource,scope,Timestamp.from(now),reason.reason(),reason.correlationId(),
        before==null?null:json.writeValueAsString(before),after==null?null:json.writeValueAsString(after));
    } catch(org.springframework.dao.DataAccessException failure) {
      metrics.counter("iiam.security.audit.failures","journal","ADMINISTRATIVE").increment();
      org.slf4j.LoggerFactory.getLogger(getClass()).error("security_audit_failure journal=ADMINISTRATIVE correlationId={}",reason.correlationId());
      throw failure;
    }
    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
      public void afterCommit() { metrics.counter("iiam.authorization.mutations","action",operation).increment(); }
    });
  }
  private void changed(int rows) { if (rows!=1) throw new OptimisticLockingFailureException("Policy changed concurrently."); }
}
