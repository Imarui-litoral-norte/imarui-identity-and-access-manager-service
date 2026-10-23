package br.com.imarui.ima.authorization.infra;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.authorization.api.http.AuthorizationAdminController.*;
import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.application.usecase.*;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.repository.ScopeRepository;
import br.com.imarui.ima.identity.core.domain.model.identity.Identity;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class AuthorizationAdminHttpIntegrationTest extends IdentityIntegrationSupport {
  @Autowired RoleAdministrationUseCases roles;
  @Autowired ScopeAdministrationUseCases hierarchy;
  @Autowired ScopeRepository scopes;
  private static final UUID PLATFORM=AuthorizationScope.PLATFORM_ID;
  private static final PolicyChangeReason REASON=new PolicyChangeReason("Approved administration",UUID.randomUUID());
  private static final String API="/api/v1/authorization/admin";
  @BeforeEach void root() {
    new TransactionTemplate(transactions).executeWithoutResult(s -> {
      jdbc.update("delete from authentication_bootstrap where name='initial-root'");
      jdbc.update("insert into authentication_bootstrap values('initial-root',?,current_timestamp)",reviewer.getId().value());
      rootAuthorization.assign(reviewer.getId().value());
    }); actor(reviewer.getId());
  }
  @AfterEach void marker() { jdbc.update("delete from authentication_bootstrap where name='initial-root'"); }
  private String liveToken(Identity identity) {
    return new TransactionTemplate(transactions).execute(s -> {
      var now=fixtureClock.instant(); var session=authenticationStore.createSession(identity.getId().value(),now);
      return tokenIssuer.issue(identity.getId().value(),session.getPublicId(),now);
    });
  }
  private UUID scope() { return scopes.tenantScope(tenant.getId().value()).orElseThrow(); }
  private String name() { return "R"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT); }
  private Identity user() { var user=identities.save(service(UUID.randomUUID()+"@example.test")); firstAccess(user.getId()); return identities.findById(user.getId()).orElseThrow(); }
  private Change change(long version) { return new Change(version,REASON.reason(),UUID.randomUUID()); }
  private Identity localAdmin() {
    var manager=user(); var role=roles.create(name(),Role.Ownership.GLOBAL,null,null,Set.of("AUTHORIZATION_AUDIT_READ","AUTHORIZATION_ROLE_MANAGE"),REASON);
    roles.assign(manager.getId().value(),role.id(),scope(),REASON); return manager;
  }

  @Test void scopeCrudReparentVersionAndIndependentDeletionHistory() throws Exception {
    var jwt=liveToken(reviewer);
    var response=http("POST",API+"/scopes",json.writeValueAsString(new ScopeCreate(name(),"Administrative grouping",PLATFORM,REASON.reason(),UUID.randomUUID())),jwt);
    assertEquals(201,response.statusCode(),response.body()); var id=UUID.fromString(json.readTree(response.body()).path("id").asText());
    var parent=hierarchy.create(name(),"Grouping parent",PLATFORM,REASON);
    var moved=http("POST",API+"/scopes/"+id+"/reparent",json.writeValueAsString(new Reparent(parent.id(),0L,REASON.reason(),UUID.randomUUID())),jwt);
    assertEquals(200,moved.statusCode(),moved.body());
    assertEquals(id.toString(),json.readTree(moved.body()).path("id").asText());
    assertEquals(409,http("POST",API+"/scopes/"+id+"/reparent",json.writeValueAsString(new Reparent(PLATFORM,0L,REASON.reason(),UUID.randomUUID())),jwt).statusCode());
    assertEquals(200,http("PATCH",API+"/scopes/"+id,json.writeValueAsString(new ScopeRevision("Disabled leaf",AuthorizationScope.Status.DISABLED,parent.id(),1L,REASON.reason(),UUID.randomUUID())),jwt).statusCode());
    assertEquals(204,http("DELETE",API+"/scopes/"+id,json.writeValueAsString(change(2)),jwt).statusCode());
    assertEquals(1,jdbc.queryForObject("select count(*) from authorization_scopes__history where original_scope_id=?",Integer.class,id));
  }
  @Test void roleCrudLifecyclePermissionsAndVersionConflicts() throws Exception {
    var jwt=liveToken(reviewer);
    var response=http("POST",API+"/roles",json.writeValueAsString(new RoleCreate(name(),Role.Ownership.TENANT,scope(),tenant.getId().value(),Set.of(),REASON.reason(),UUID.randomUUID())),jwt);
    assertEquals(201,response.statusCode(),response.body()); long id=json.readTree(response.body()).path("id").asLong();
    var permissions=new Permissions(Set.of("IDENTITY_READ"),0L,REASON.reason(),UUID.randomUUID());
    assertEquals(200,http("PUT",API+"/roles/"+id+"/permissions",json.writeValueAsString(permissions),jwt).statusCode());
    assertEquals(409,http("PUT",API+"/roles/"+id+"/permissions",json.writeValueAsString(permissions),jwt).statusCode());
    var archived=new Lifecycle(Role.Status.ARCHIVED,1L,REASON.reason(),UUID.randomUUID());
    assertEquals(200,http("POST",API+"/roles/"+id+"/lifecycle",json.writeValueAsString(archived),jwt).statusCode());
    assertEquals(204,http("DELETE",API+"/roles/"+id,json.writeValueAsString(change(2)),jwt).statusCode());
  }
  @Test void localAdminSeesSharedRolesButCannotEditTheirOwnerOrInspectSiblings() throws Exception {
    var manager=localAdmin(); var jwt=liveToken(manager);
    var shared=roles.create(name(),Role.Ownership.SCOPE,PLATFORM,null,Set.of("IDENTITY_READ"),REASON);
    var available=http("GET",API+"/roles/available?scopeId="+scope()+"&size=100",null,jwt);
    assertEquals(200,available.statusCode(),available.body());
    var items=json.readTree(available.body()).path("items");
    var sharedView=java.util.stream.StreamSupport.stream(items.spliterator(),false).filter(n -> n.path("id").asLong()==shared.id()).findFirst().orElseThrow();
    assertTrue(sharedView.path("available").asBoolean()); assertFalse(sharedView.path("manageable").asBoolean());
    assertEquals(403,http("PATCH",API+"/roles/"+shared.id(),json.writeValueAsString(new RoleRevision(shared.name(),Role.Status.ACTIVE,Set.of(),999L,REASON.reason(),UUID.randomUUID())),jwt).statusCode());
    var sibling=hierarchy.create(name(),"Sibling",PLATFORM,REASON);
    assertEquals(403,http("GET",API+"/scopes?scopeId="+sibling.id(),null,jwt).statusCode());
    assertEquals(403,http("GET",API+"/roles/"+shared.id()+"/permissions",null,jwt).statusCode());
  }
  @Test void aQueuedAdministrativeMutationRevalidatesItsRevokedHumanSessionAfterThePolicyLock() throws Exception {
    var manager=localAdmin(); var jwt=liveToken(manager); var roleName=name();
    var body=json.writeValueAsString(new RoleCreate(roleName,Role.Ownership.TENANT,scope(),tenant.getId().value(),Set.of(),REASON.reason(),UUID.randomUUID()));
    var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
    try(var connection=jdbc.getDataSource().getConnection();var statement=connection.createStatement()) {
      statement.execute("select pg_advisory_lock(810082)");
      var pending=executor.submit(() -> http("POST",API+"/roles",body,jwt));
      try {
        boolean queued=false; long deadline=System.nanoTime()+java.time.Duration.ofSeconds(2).toNanos();
        while(System.nanoTime()<deadline) {
          queued=jdbc.queryForObject("select exists(select 1 from pg_stat_activity where datname=current_database() and wait_event='advisory' and query like '%pg_advisory_xact_lock(810082)%')",Boolean.class);
          if(queued) break; Thread.sleep(15);
        }
        assertTrue(queued,"The request must already be authenticated and waiting for the policy lock");
        jdbc.update("update authentication_sessions set session_status='REVOKED',revoked_at=current_timestamp where identity_id=?",manager.getId().value());
      } finally { statement.execute("select pg_advisory_unlock(810082)"); }
      var response=pending.get(10,java.util.concurrent.TimeUnit.SECONDS);
      assertEquals(401,response.statusCode(),response.body());
      assertEquals(0,jdbc.queryForObject("select count(*) from authorization_roles where name=?",Integer.class,roleName));
    } finally { executor.shutdownNow(); }
  }
  @Test void denyApiIsIdempotentAndRevocationHasAnExplicitVersion() throws Exception {
    var target=user(); var jwt=liveToken(reviewer);
    var block=new Block("IDENTITY_READ",scope(),target.getId().value(),null,REASON.reason(),UUID.randomUUID()); var body=json.writeValueAsString(block);
    var a=http("POST",API+"/denies",body,jwt); var c=http("POST",API+"/denies",body,jwt);
    assertEquals(200,a.statusCode(),a.body()); assertEquals(json.readTree(a.body()).path("id"),json.readTree(c.body()).path("id"));
    var id=json.readTree(a.body()).path("id").asText();
    assertEquals(204,http("POST",API+"/denies/"+id+"/revoke",json.writeValueAsString(change(0)),jwt).statusCode());
    assertEquals(409,http("POST",API+"/denies/"+id+"/revoke",json.writeValueAsString(change(0)),jwt).statusCode());
  }
  @Test void diagnosticsExplainInheritedDenyAndAreProtectedSeparatelyFromManagement() throws Exception {
    var manager=localAdmin(); var target=user();
    var reader=roles.create(name(),Role.Ownership.GLOBAL,null,null,Set.of("IDENTITY_READ"),REASON);
    roles.assign(target.getId().value(),reader.id(),PLATFORM,REASON);
    var rootJwt=liveToken(reviewer);
    http("POST",API+"/denies",json.writeValueAsString(new Block("IDENTITY_READ",PLATFORM,target.getId().value(),null,REASON.reason(),UUID.randomUUID())),rootJwt);
    var path=API+"/decisions/explain?identityId="+target.getId().value()+"&permission=IDENTITY_READ&scopeId="+scope();
    var response=http("GET",path,null,liveToken(manager)); assertEquals(200,response.statusCode(),response.body());
    var result=json.readTree(response.body()); assertFalse(result.path("decision").path("allowed").asBoolean());
    assertEquals("EXPLICIT_DENY",result.path("decision").path("reason").asText());
    assertTrue(result.path("sources").get(0).path("inherited").asBoolean());
    assertEquals(403,http("GET",path,null,liveToken(target)).statusCode());
  }
  @Test void malformedUnauthorizedPaginationAndMissingResourceUseProblemDetails() throws Exception {
    var jwt=liveToken(reviewer);
    assertEquals(401,http("GET",API+"/scopes?scopeId="+PLATFORM,null,null).statusCode());
    var bad=http("POST",API+"/roles","{}",jwt); assertEquals(400,bad.statusCode(),bad.body());
    assertTrue(bad.headers().firstValue("Content-Type").orElse("").contains("application/problem+json"));
    assertEquals(400,http("GET",API+"/scopes?scopeId="+PLATFORM+"&size=0",null,jwt).statusCode());
    assertEquals(404,http("GET",API+"/roles/9223372036854775806",null,jwt).statusCode());
  }
  @Test void openApiContainsAdministrativeAndTechnicalContractsAndExplicitLifecycle() throws Exception {
    var response=http("GET","/v3/api-docs",null,null); assertEquals(200,response.statusCode(),response.body());
    var document=json.readTree(response.body());
    assertTrue(document.path("paths").has(API+"/roles/{id}/permissions"));
    assertTrue(document.path("paths").has("/api/v1/authorization/service/decisions"));
    assertTrue(response.body().contains("ARCHIVED"));
    java.nio.file.Files.writeString(java.nio.file.Path.of("target","authorization-openapi.json"),response.body());
  }
  @Test void securityAuditIsPaginatedScopedAndImmutableIncludingDeletedScopeHistory() throws Exception {
    var manager=localAdmin(); var managerJwt=liveToken(manager); var rootJwt=liveToken(reviewer);
    var inside=hierarchy.create(name(),"Historical leaf",scope(),REASON);
    hierarchy.revise(inside.id(),inside.name(),AuthorizationScope.Status.DISABLED,inside.parentId(),REASON);
    hierarchy.delete(inside.id(),REASON);
    var response=http("GET",API+"/audit?scopeId="+scope()+"&event=SCOPE_DELETED&size=100",null,managerJwt);
    assertEquals(200,response.statusCode(),response.body());
    assertTrue(response.body().contains(inside.id().toString()),response.body());
    var sibling=hierarchy.create(name(),"Outside company",PLATFORM,REASON);
    assertEquals(403,http("GET",API+"/audit?scopeId="+sibling.id(),null,managerJwt).statusCode());
    assertEquals(400,http("GET",API+"/audit?scopeId="+PLATFORM+"&size=101",null,rootJwt).statusCode());
    assertThrows(org.springframework.dao.DataIntegrityViolationException.class,() -> jdbc.update("update authorization_audit set action='TAMPERED' where id=(select id from authorization_audit limit 1)"));
    assertThrows(org.springframework.dao.DataIntegrityViolationException.class,() -> jdbc.update("delete from authorization_scopes__history where original_scope_id=?",inside.id()));
  }
  @Test void activeAncestorCanInspectADisabledScopeForSafeLifecycleAdministration() throws Exception {
    var leaf=hierarchy.create(name(),"Disabled configuration",PLATFORM,REASON);
    hierarchy.revise(leaf.id(),leaf.name(),AuthorizationScope.Status.DISABLED,PLATFORM,REASON);
    var response=http("GET",API+"/scopes/"+leaf.id(),null,liveToken(reviewer));
    assertEquals(200,response.statusCode(),response.body());
    assertEquals("DISABLED",json.readTree(response.body()).path("status").asText());
  }
}
