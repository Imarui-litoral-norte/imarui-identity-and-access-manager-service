package br.com.imarui.ima.authorization.infra;

import br.com.imarui.ima.authorization.core.domain.model.AuthorizationScope;
import br.com.imarui.ima.authorization.core.domain.policy.RootRecoveryEnvelope;
import br.com.imarui.ima.authorization.infra.persistence.*;
import br.com.imarui.ima.identity.api.module.IdentityAuthorizationApi;
import com.zaxxer.hikari.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Each run owns a fresh schema. Never truncates shared fixtures or operational history. */
final class OperationalPolicyFixture implements AutoCloseable {
  final String url=System.getenv().getOrDefault("IDENTITY_TEST_DB_URL","jdbc:postgresql://localhost:15437/identity_test");
  final String schema="auth_ops_"+UUID.randomUUID().toString().replace("-","");
  final HikariDataSource source;
  final JdbcTemplate jdbc;
  final TransactionTemplate transaction,read,decision;
  final SimpleMeterRegistry metrics=new SimpleMeterRegistry();
  final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();
  final UUID operator=id("operator"),subject=id("identity-1"),left=id("left"),right=id("right"),leaf=id("scope-1");
  final JdbcScopeRepository scopes;
  final JdbcAuthorizationPolicyRepository policies;
  final JdbcAuthorizationMutationRepository mutations;
  final JdbcSecurityAuditEvents events;

  OperationalPolicyFixture(int identities,int scopeCount) {
    this(identities,scopeCount,null);
  }
  OperationalPolicyFixture(int identities,int scopeCount,String target) {
    if(!url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/identity_test"))
      throw new IllegalArgumentException("Operational rehearsal requires the explicit local identity_test database.");
    var configuration=Flyway.configure().dataSource(url,"identity_test","identity_test").schemas(schema).defaultSchema(schema);
    if(target!=null) configuration.target(target);
    configuration.load().migrate();
    var config=new HikariConfig(); config.setJdbcUrl(url); config.setUsername("identity_test"); config.setPassword("identity_test");
    config.setSchema(schema); config.setMaximumPoolSize(8); config.setMinimumIdle(0); source=new HikariDataSource(config);
    jdbc=new JdbcTemplate(source); var manager=new DataSourceTransactionManager(source);
    transaction=new TransactionTemplate(manager); transaction.setTimeout(30);
    decision=new TransactionTemplate(manager); decision.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ); decision.setTimeout(5);
    read=new TransactionTemplate(manager); read.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ); read.setReadOnly(true);
    scopes=new JdbcScopeRepository(jdbc);
    var identityPort=new IdentityAuthorizationApi() {
      public Set<UUID> activeIdentityIds() { return Set.copyOf(jdbc.queryForList("select id from ima_identities where status='ACTIVE'",UUID.class)); }
      public Set<UUID> activeTenantIds() { return Set.copyOf(jdbc.queryForList("select id from ima_tenants where status='ACTIVE'",UUID.class)); }
      public boolean tenantExists(UUID id) { return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from ima_tenants where id=?)",Boolean.class,id)); }
    };
    policies=new JdbcAuthorizationPolicyRepository(jdbc,scopes,identityPort,metrics);
    mutations=new JdbcAuthorizationMutationRepository(jdbc,json,metrics);
    events=new JdbcSecurityAuditEvents(jdbc,Clock.systemUTC(),json,metrics);
    try { transaction.executeWithoutResult(s -> seed(identities,scopeCount)); }
    catch(RuntimeException failure) { close(); throw failure; }
  }
  static UUID id(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)); }
  private void seed(int identities,int scopeCount) {
    var permissions=new HashSet<>(RootRecoveryEnvelope.CAPABILITIES); permissions.add("IDENTITY_READ");
    for(var code:permissions) jdbc.update("insert into authorization_permissions(code,description,module,service_code,namespace,catalog_version,updated_at) values(?,?,'AUTHORIZATION','iiam','iiam',1,current_timestamp) on conflict(code) do nothing",code,code);
    for(int n=0;n<=identities;n++) {
      UUID identity=n==0?operator:id("identity-"+n);
      jdbc.update("""
          insert into ima_identities(id,kind,primary_email_id,primary_email,primary_email_type,primary_email_verified,
          primary_email_created_at,status,created_at,updated_at,version,nickname)
          values(?,'SERVICE',?,?, 'PRIMARY',true,current_timestamp,'ACTIVE',current_timestamp,current_timestamp,0,?)
          """,identity,id("email-"+n),"synthetic-"+n+"@example.test","synthetic-"+n);
      jdbc.update("insert into ima_service_identities(id,name) values(?,'Operational fixture')",identity);
    }
    scope(left,"OPS_LEFT",AuthorizationScope.PLATFORM_ID); scope(right,"OPS_RIGHT",AuthorizationScope.PLATFORM_ID);
    for(int n=1;n<=scopeCount;n++) scope(id("scope-"+n),"OPS_"+n,left);
    jdbc.update("insert into authorization_roles(id,name,ownership,status) values(900001,'ROOT','GLOBAL','ACTIVE'),(900002,'OPS_SHARED','GLOBAL','ACTIVE')");
    jdbc.update("insert into authorization_role_permissions(role_id,permission_id) select 900001,id from authorization_permissions");
    jdbc.update("insert into authorization_role_permissions(role_id,permission_id) select 900002,id from authorization_permissions where code='IDENTITY_READ'");
    jdbc.update("insert into authorization_identity_roles(identity_id,role_id,authorization_scope_id) values(?,900001,?)",operator,AuthorizationScope.PLATFORM_ID);
    for(var code:permissions) delegation(operator,code,AuthorizationScope.PLATFORM_ID);
    for(int n=1;n<=identities;n++) {
      jdbc.update("insert into authorization_identity_roles(identity_id,role_id,authorization_scope_id) values(?,900002,?)",id("identity-"+n),left);
      if(n%10==0) {
        delegation(id("identity-"+n),"IDENTITY_READ",left);
        jdbc.update("insert into authorization_denies(id,permission_code,scope_id,identity_id,reason,created_at,updated_at) values(?,'IDENTITY_READ',?,?,'Synthetic deny',current_timestamp,current_timestamp)",id("deny-"+n),leaf,id("identity-"+n));
      }
    }
    jdbc.update("insert into authentication_service_clients(service_code,client_id,enabled,operations,created_at,updated_at) values('iiam',?,true,'PDP_DECIDE',current_timestamp,current_timestamp)",id("client"));
    jdbc.update("insert into authorization_service_resources(service_code,resource_type,resource_id,scope_id,created_at) values('iiam','OBJECT','synthetic-object',?,current_timestamp)",leaf);
  }
  private void scope(UUID scope,String code,UUID parent) {
    jdbc.update("insert into authorization_scopes(id,code,name,status,parent_id,created_at,updated_at) values(?,?,'Operational fixture','ACTIVE',?,current_timestamp,current_timestamp)",scope,code,parent);
  }
  private void delegation(UUID identity,String permission,UUID scope) {
    jdbc.update("insert into authorization_permission_delegations(id,identity_id,permission_code,scope_id,redelegable,reason,created_at,updated_at) values(?,?,?,?,true,'Synthetic authority',current_timestamp,current_timestamp)",UUID.randomUUID(),identity,permission,scope);
  }
  public void close() {
    source.close(); metrics.close();
    if(!schema.matches("auth_ops_[0-9a-f]{32}")) throw new IllegalStateException("Unexpected rehearsal schema");
    var admin=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,"identity_test","identity_test");
    new JdbcTemplate(admin).execute("drop schema "+schema+" cascade");
  }
}
