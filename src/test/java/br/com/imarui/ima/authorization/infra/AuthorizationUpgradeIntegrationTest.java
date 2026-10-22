package br.com.imarui.ima.authorization.infra;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.authorization.core.domain.model.AuthorizationScope;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuthorizationUpgradeIntegrationTest extends IdentityIntegrationSupport {
  @Autowired DataSource source;
  private String url() throws SQLException { try (var c=source.getConnection()) { return c.getMetaData().getURL(); } }
  private Flyway flyway(String url,String schema,String target) {
    var configuration=Flyway.configure().dataSource(url,"identity_test","identity_test").schemas(schema).defaultSchema(schema);
    if (target!=null) configuration.target(target);
    return configuration.load();
  }

  @Test void populatedV10PreservesIdsGrantsIdentitySessionsRefreshAndTenantBinding() throws Exception {
    String schema="auth_upgrade_"+UUID.randomUUID().toString().replace("-","");
    String url=url(); var identity=UUID.randomUUID(); var tenantId=UUID.randomUUID();
    var emailId=UUID.randomUUID(); var sid=UUID.randomUUID();
    try (var c=DriverManager.getConnection(url,"identity_test","identity_test")) {
      flyway(url,schema,"10").migrate(); c.setSchema(schema);
      try (var s=c.createStatement()) {
        s.execute("insert into ima_tenants(id,code,name,status,created_at,updated_at,version) values('"+tenantId+"','UPGRADE','Upgrade tenant','ACTIVE',current_timestamp,current_timestamp,0)");
        s.execute("insert into ima_identities(id,kind,primary_email_id,primary_email,primary_email_type,primary_email_verified,primary_email_created_at,status,created_at,updated_at,version,nickname) values('"+identity+"','SERVICE','"+emailId+"','upgrade@example.test','PRIMARY',true,current_timestamp,'ACTIVE',current_timestamp,current_timestamp,0,'upgrade-service')");
        s.execute("insert into ima_service_identities(id,name) values('"+identity+"','Upgrade service')");
        s.execute("insert into authorization_permissions(id,code,description,module) values(89001,'IDENTITY_READ','Historical definition','IDENTITY')");
        s.execute("insert into authorization_roles(id,name,active) values(89002,'UPGRADE_ROLE',true)");
        s.execute("insert into authorization_role_permissions(id,role_id,permission_id) values(89003,89002,89001)");
        s.execute("insert into authorization_identity_roles(identity_id,role_id,scope,created_at) values('"+identity+"',89002,'PLATFORM','2026-01-01T00:00:00Z')");
        s.execute("insert into authentication_sessions(id,identity_id,public_id,created_at,expires_at,session_status) values(89004,'"+identity+"','"+sid+"',current_timestamp,current_timestamp+interval '7 days','ACTIVE')");
        s.execute("insert into authentication_refresh_tokens(id,session_id,token_hash,created_at,expires_at,status) values(89005,89004,'upgrade-refresh-hash',current_timestamp,current_timestamp+interval '7 days','ACTIVE')");
      }
      flyway(url,schema,null).migrate(); flyway(url,schema,null).validate();
      try (var s=c.createStatement(); var rows=s.executeQuery("select ir.id,ir.identity_id,ir.role_id,ir.authorization_scope_id,ir.created_at,ir.active,r.ownership,rp.id,p.id,p.service_code from authorization_identity_roles ir join authorization_roles r on r.id=ir.role_id join authorization_role_permissions rp on rp.role_id=r.id join authorization_permissions p on p.id=rp.permission_id")) {
        assertTrue(rows.next()); assertNotNull(rows.getObject(1)); assertEquals(identity,rows.getObject(2,UUID.class));
        assertEquals(89002,rows.getLong(3)); assertEquals(AuthorizationScope.PLATFORM_ID,rows.getObject(4,UUID.class));
        assertEquals(java.time.Instant.parse("2026-01-01T00:00:00Z"),rows.getTimestamp(5).toInstant());
        assertTrue(rows.getBoolean(6)); assertEquals("GLOBAL",rows.getString(7));
        assertEquals(89003,rows.getLong(8)); assertEquals(89001,rows.getLong(9)); assertEquals("iiam",rows.getString(10));
        assertFalse(rows.next());
      }
      try (var s=c.createStatement(); var rows=s.executeQuery("select scope_id from authorization_tenant_scopes where tenant_id='"+tenantId+"'")) {
        assertTrue(rows.next()); assertNotEquals(tenantId,rows.getObject(1,UUID.class));
      }
      try (var s=c.createStatement(); var rows=s.executeQuery("select i.nickname,i.status,s.public_id,s.session_status,r.status from ima_identities i join authentication_sessions s on s.identity_id=i.id join authentication_refresh_tokens r on r.session_id=s.id")) {
        assertTrue(rows.next()); assertEquals("upgrade-service",rows.getString(1)); assertEquals("ACTIVE",rows.getString(2));
        assertEquals(sid,rows.getObject(3,UUID.class)); assertEquals("ACTIVE",rows.getString(4)); assertEquals("ACTIVE",rows.getString(5));
      }
    } finally { jdbc.execute("drop schema if exists "+schema+" cascade"); }
  }

  @Test void populatedV17ReconcilesEquivalentBlocksWithoutDeletingHistoryAndPreservesRoleLifecycle() throws Exception {
    String schema="auth_v17_"+UUID.randomUUID().toString().replace("-",""); String url=url();
    var first=UUID.randomUUID(); var duplicate=UUID.randomUUID();
    try (var c=DriverManager.getConnection(url,"identity_test","identity_test")) {
      flyway(url,schema,"17").migrate(); c.setSchema(schema);
      try(var s=c.createStatement()) {
        s.execute("insert into authorization_permissions(id,code,description,module,service_code,namespace,catalog_version,updated_at) values(88001,'IDENTITY_READ','read','IDENTITY','iiam','iiam',1,current_timestamp)");
        s.execute("insert into authorization_roles(id,name,ownership,active) values(88002,'OLD_INACTIVE','GLOBAL',false)");
        s.execute("insert into authorization_denies(id,permission_code,scope_id,role_id,reason,created_at,updated_at) values('"+first+"','IDENTITY_READ','"+AuthorizationScope.PLATFORM_ID+"',88002,'original','2026-01-01Z','2026-01-01Z')");
        s.execute("insert into authorization_denies(id,permission_code,scope_id,role_id,reason,created_at,updated_at) values('"+duplicate+"','IDENTITY_READ','"+AuthorizationScope.PLATFORM_ID+"',88002,'duplicate','2026-01-02Z','2026-01-02Z')");
      }
      flyway(url,schema,null).migrate(); flyway(url,schema,null).validate();
      try(var s=c.createStatement();var rows=s.executeQuery("select count(*),count(*) filter(where active) from authorization_denies")) {
        assertTrue(rows.next()); assertEquals(2,rows.getInt(1)); assertEquals(1,rows.getInt(2));
      }
      try(var s=c.createStatement();var rows=s.executeQuery("select active,reason from authorization_denies where id='"+first+"'")) {
        assertTrue(rows.next()); assertTrue(rows.getBoolean(1)); assertEquals("original",rows.getString(2));
      }
      try(var s=c.createStatement();var rows=s.executeQuery("select count(*) from authorization_audit where action='DUPLICATE_DENY_RECONCILED' and resource_id='"+duplicate+"' and before_state->>'active'='true' and after_state->>'active'='false'")) {
        assertTrue(rows.next()); assertEquals(1,rows.getInt(1));
      }
      try(var s=c.createStatement();var rows=s.executeQuery("select status,active from authorization_roles where id=88002")) {
        assertTrue(rows.next()); assertEquals("INACTIVE",rows.getString(1)); assertFalse(rows.getBoolean(2));
      }
    } finally { jdbc.execute("drop schema if exists "+schema+" cascade"); }
  }

  @Test void legacyRootUpgradePreservesTheRoleIdAndSeedsAllExplicitRecoveryAuthority() throws Exception {
    String schema="auth_root_upgrade_"+UUID.randomUUID().toString().replace("-",""); String url=url();
    var identity=UUID.randomUUID(); var assignment=UUID.randomUUID();
    try(var c=DriverManager.getConnection(url,"identity_test","identity_test")) {
      flyway(url,schema,"10").migrate(); c.setSchema(schema);
      try(var s=c.createStatement()) {
        s.execute("insert into ima_identities(id,kind,primary_email_id,primary_email,primary_email_type,primary_email_verified,primary_email_created_at,status,created_at,updated_at,version,nickname) values('"+identity+"','SERVICE','"+UUID.randomUUID()+"','legacy-root@example.test','PRIMARY',true,current_timestamp,'ACTIVE',current_timestamp,current_timestamp,0,'legacy-root')");
        s.execute("insert into ima_service_identities(id,name) values('"+identity+"','Legacy ROOT')");
        s.execute("insert into authorization_permissions(id,code,description,module) values(87001,'IDENTITY_READ','Historical read','IDENTITY')");
        s.execute("insert into authorization_roles(id,name,active) values(87002,'ROOT',true)");
        s.execute("insert into authorization_role_permissions(id,role_id,permission_id) values(87003,87002,87001)");
        s.execute("insert into authorization_identity_roles(identity_id,role_id,scope) values('"+identity+"',87002,'PLATFORM')");
      }
      flyway(url,schema,"17").migrate();
      try(var s=c.createStatement();var rows=s.executeQuery("select id from authorization_identity_roles where identity_id='"+identity+"'")) {
        assertTrue(rows.next()); assignment=rows.getObject(1,UUID.class);
      }
      flyway(url,schema,null).migrate(); flyway(url,schema,null).validate();
      try(var s=c.createStatement();var rows=s.executeQuery("select role_id,authorization_scope_id from authorization_identity_roles where id='"+assignment+"'")) {
        assertTrue(rows.next()); assertEquals(87002,rows.getLong(1)); assertEquals(AuthorizationScope.PLATFORM_ID,rows.getObject(2,UUID.class));
      }
      for(var code:br.com.imarui.ima.authorization.core.domain.policy.RootRecoveryEnvelope.CAPABILITIES) {
        try(var s=c.prepareStatement("select count(*) from authorization_permission_delegations where identity_id=? and permission_code=? and scope_id=? and active and redelegable")) {
          s.setObject(1,identity); s.setString(2,code); s.setObject(3,AuthorizationScope.PLATFORM_ID);
          try(var r=s.executeQuery()) { assertTrue(r.next()); assertEquals(1,r.getInt(1),code); }
        }
      }
      try(var s=c.createStatement();var rows=s.executeQuery("select id from authorization_role_permissions where role_id=87002 and permission_id=87001")) {
        assertTrue(rows.next()); assertEquals(87003,rows.getLong(1));
      }
    } finally { jdbc.execute("drop schema if exists "+schema+" cascade"); }
  }

  @Test void populatedV20PreservesTechnicalCredentialsNoncesJurisdictionAndSecurityHistory() throws Exception {
    String schema="auth_m2m_upgrade_"+UUID.randomUUID().toString().replace("-",""); String url=url();
    var client=UUID.randomUUID(); var key=UUID.randomUUID(); var nonce=UUID.randomUUID(); var event=UUID.randomUUID();
    var generator=java.security.KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
    var publicKey=Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
    try(var c=DriverManager.getConnection(url,"identity_test","identity_test")) {
      flyway(url,schema,"20").migrate(); c.setSchema(schema);
      try(var s=c.createStatement()) {
        s.execute("insert into authorization_catalog_owners(service_code,namespace,updated_at) values('upgrade-service','upgrade-service',current_timestamp)");
        s.execute("insert into authentication_service_clients(service_code,client_id,enabled,operations,created_at,updated_at,version) values('upgrade-service','"+client+"',false,'CATALOG_SYNC',current_timestamp,current_timestamp,4)");
        s.execute("insert into authentication_service_keys(id,service_code,public_key,fingerprint,active,created_at,expires_at,revoked_at) values('"+key+"','upgrade-service','"+publicKey+"','"+"a".repeat(64)+"',false,current_timestamp,current_timestamp+interval '1 day',current_timestamp)");
        s.execute("insert into authentication_service_nonces values('"+key+"','"+nonce+"',current_timestamp+interval '1 minute')");
        s.execute("insert into authorization_service_jurisdictions values('upgrade-service','"+AuthorizationScope.PLATFORM_ID+"')");
        s.execute("insert into authorization_security_events(id,event,service_code,resource_id,occurred_at,reason_code,correlation_id) values('"+event+"','M2M_AUTHENTICATION','upgrade-service','"+key+"',current_timestamp,'INVALID_CREDENTIAL','"+UUID.randomUUID()+"')");
      }
      flyway(url,schema,null).migrate(); flyway(url,schema,null).validate();
      try(var s=c.createStatement();var rows=s.executeQuery("select c.client_id,c.enabled,c.version,c.operations,k.id,k.public_key,k.active,k.revoked_at,n.nonce,j.scope_id from authentication_service_clients c join authentication_service_keys k using(service_code) join authentication_service_nonces n on n.credential_id=k.id join authorization_service_jurisdictions j using(service_code)")) {
        assertTrue(rows.next()); assertEquals(client,rows.getObject(1,UUID.class)); assertFalse(rows.getBoolean(2));
        assertEquals(4,rows.getLong(3)); assertEquals("CATALOG_SYNC",rows.getString(4));
        assertEquals(key,rows.getObject(5,UUID.class)); assertEquals(publicKey,rows.getString(6)); assertFalse(rows.getBoolean(7));
        assertNotNull(rows.getTimestamp(8)); assertEquals(nonce,rows.getObject(9,UUID.class)); assertEquals(AuthorizationScope.PLATFORM_ID,rows.getObject(10,UUID.class));
        assertFalse(rows.next());
      }
      try(var s=c.createStatement();var rows=s.executeQuery("select id,reason_code,resource_id from authorization_security_events")) {
        assertTrue(rows.next()); assertEquals(event,rows.getObject(1,UUID.class)); assertEquals("INVALID_CREDENTIAL",rows.getString(2)); assertEquals(key.toString(),rows.getString(3));
      }
    } finally { jdbc.execute("drop schema if exists "+schema+" cascade"); }
  }

  @Test void cleanInstallationHasOnlyTheAdministrativeScopeAndNoAutomaticAssignment() throws Exception {
    String schema="auth_fresh_"+UUID.randomUUID().toString().replace("-",""); String url=url();
    try (var c=DriverManager.getConnection(url,"identity_test","identity_test")) {
      flyway(url,schema,null).migrate(); flyway(url,schema,null).validate(); c.setSchema(schema);
      try (var s=c.createStatement(); var rows=s.executeQuery("select (select count(*) from authorization_scopes),(select count(*) from authorization_identity_roles),(select count(*) from ima_identities),(select count(*) from authorization_denies)")) {
        assertTrue(rows.next()); assertEquals(1,rows.getInt(1)); assertEquals(0,rows.getInt(2)); assertEquals(0,rows.getInt(3)); assertEquals(0,rows.getInt(4));
      }
    } finally { jdbc.execute("drop schema if exists "+schema+" cascade"); }
  }
}
