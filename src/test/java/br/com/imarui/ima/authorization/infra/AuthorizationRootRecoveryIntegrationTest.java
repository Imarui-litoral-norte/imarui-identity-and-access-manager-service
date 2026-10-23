package br.com.imarui.ima.authorization.infra;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.authorization.api.http.RootRecoveryController;
import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.application.usecase.*;
import br.com.imarui.ima.authorization.core.domain.model.AuthorizationScope;
import br.com.imarui.ima.authorization.core.repository.ScopeRepository;
import br.com.imarui.ima.identity.core.domain.model.identity.Identity;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class AuthorizationRootRecoveryIntegrationTest extends IdentityIntegrationSupport {
  @Autowired DenyDelegationUseCases denies;
  @Autowired EffectiveAuthorizationUseCases decisions;
  @Autowired ScopeRepository scopes;
  @Autowired ScopeAdministrationUseCases hierarchy;
  @Autowired RoleAdministrationUseCases roles;
  private static final String PATH="/api/v1/authorization/admin/root/transfers";
  private static final String PASSWORD="Fixture password 123!";
  private static final UUID PLATFORM=AuthorizationScope.PLATFORM_ID;
  private static final PolicyChangeReason REASON=new PolicyChangeReason("Recovery safety verification",UUID.randomUUID());

  @BeforeEach void root() {
    new TransactionTemplate(transactions).executeWithoutResult(s -> {
      jdbc.update("delete from authentication_bootstrap where name='initial-root'");
      jdbc.update("insert into authentication_bootstrap values('initial-root',?,current_timestamp)",reviewer.getId().value());
      rootAuthorization.assign(reviewer.getId().value());
    });
    actor(reviewer.getId());
  }
  @AfterEach void marker() { jdbc.update("delete from authentication_bootstrap where name='initial-root'"); }
  private String liveToken(Identity identity) {
    return new TransactionTemplate(transactions).execute(s -> {
      var now=fixtureClock.instant(); var session=authenticationStore.createSession(identity.getId().value(),now);
      return tokenIssuer.issue(identity.getId().value(),session.getPublicId(),now);
    });
  }
  private Identity user() {
    var user=identities.save(service(UUID.randomUUID()+"@example.test")); firstAccess(user.getId());
    return identities.findById(user.getId()).orElseThrow();
  }
  private String initiate(Identity recipient,String password,String jwt) throws Exception {
    return http("POST",PATH,json.writeValueAsString(new RootRecoveryController.Initiate(recipient.getId().value(),PLATFORM,password,
        "Approved boundary transfer",UUID.randomUUID())),jwt).body();
  }
  private String confirmBody(String password) { return json.writeValueAsString(new RootRecoveryController.Confirm(password)); }
  private UUID approval(Identity recipient,String jwt) throws Exception {
    var response=http("POST",PATH,json.writeValueAsString(new RootRecoveryController.Initiate(recipient.getId().value(),PLATFORM,PASSWORD,
        "Approved boundary transfer",UUID.randomUUID())),jwt);
    assertEquals(200,response.statusCode(),response.body()); return UUID.fromString(json.readTree(response.body()).path("id").asText());
  }

  @Test void dualReauthenticationTransfersExactBoundaryWithoutChangingIdsOrBypassingDeny() throws Exception {
    var recipient=user(); String sourceJwt=liveToken(reviewer), targetJwt=liveToken(recipient);
    long root=jdbc.queryForObject("select id from authorization_roles where name='ROOT'",Long.class);
    var id=approval(recipient,sourceJwt);
    assertFalse(decisions.evaluate(recipient.getId().value(),"AUTHORIZATION_ROLE_MANAGE",PLATFORM).allowed());
    var result=http("POST",PATH+"/"+id+"/confirm",confirmBody(PASSWORD),targetJwt);
    assertEquals(200,result.statusCode(),result.body()); assertTrue(result.body().contains("COMPLETED"));
    assertTrue(decisions.evaluate(recipient.getId().value(),"AUTHORIZATION_ROLE_MANAGE",PLATFORM).allowed());
    assertFalse(decisions.evaluate(reviewer.getId().value(),"AUTHORIZATION_ROLE_MANAGE",PLATFORM).allowed());
    assertEquals(1,jdbc.queryForObject("select count(*) from authorization_identity_roles where active and role_id=? and authorization_scope_id=?",Integer.class,root,PLATFORM));
    assertEquals(200,http("POST",PATH+"/"+id+"/confirm",confirmBody(PASSWORD),targetJwt).statusCode());
    assertEquals(1,jdbc.queryForObject("select count(*) from authorization_audit where action='ROOT_TRANSFER_CONFIRMED' and resource_id=?",Integer.class,id.toString()));
    assertEquals(0,jdbc.queryForObject("select count(*) from authorization_audit where before_state::text like ? or after_state::text like ?",Integer.class,"%"+PASSWORD+"%","%"+sourceJwt+"%"));
  }
  @Test void wrongPasswordIsRejectedAndAttemptsPersist() throws Exception {
    var recipient=user(); var jwt=liveToken(reviewer);
    var response=http("POST",PATH,json.writeValueAsString(new RootRecoveryController.Initiate(recipient.getId().value(),PLATFORM,"Wrong password value",
        "Unauthorized attempt",UUID.randomUUID())),jwt);
    assertEquals(401,response.statusCode(),response.body());
    assertEquals(1,authenticationStore.credential(reviewer.getId().value()).orElseThrow().failures());
    assertEquals(0,jdbc.queryForObject("select count(*) from authorization_root_transfers where initiator_id=?",Integer.class,reviewer.getId().value()));
  }
  @Test void genericAdminOrOtherRecipientCannotCreateOrConsumeRootApproval() throws Exception {
    var recipient=user(); var outsider=user(); var sourceJwt=liveToken(reviewer); var targetJwt=liveToken(recipient);
    var request=new RootRecoveryController.Initiate(outsider.getId().value(),PLATFORM,PASSWORD,"Escalation attempt",UUID.randomUUID());
    assertEquals(403,http("POST",PATH,json.writeValueAsString(request),targetJwt).statusCode());
    var id=approval(recipient,sourceJwt);
    assertEquals(403,http("POST",PATH+"/"+id+"/confirm",confirmBody(PASSWORD),liveToken(outsider)).statusCode());
    assertTrue(decisions.evaluate(reviewer.getId().value(),"AUTHORIZATION_ROLE_MANAGE",PLATFORM).allowed());
  }
  @Test void revokedInitiatorSessionInvalidatesAPendingApproval() throws Exception {
    var recipient=user(); var id=approval(recipient,liveToken(reviewer));
    jdbc.update("update authentication_sessions set session_status='REVOKED',revoked_at=current_timestamp where identity_id=?",reviewer.getId().value());
    assertEquals(403,http("POST",PATH+"/"+id+"/confirm",confirmBody(PASSWORD),liveToken(recipient)).statusCode());
    assertNull(jdbc.queryForObject("select confirmed_at from authorization_root_transfers where id=?",java.sql.Timestamp.class,id));
  }
  @Test void expiredApprovalAndSelfTransferAreRejected() throws Exception {
    var recipient=user(); var jwt=liveToken(reviewer);
    var self=new RootRecoveryController.Initiate(reviewer.getId().value(),PLATFORM,PASSWORD,"Self transfer",UUID.randomUUID());
    assertEquals(403,http("POST",PATH,json.writeValueAsString(self),jwt).statusCode());
    var id=approval(recipient,jwt);
    // Controlled fixture ages the request, without changing a production Clock or weakening expiry.
    jdbc.update("update authorization_root_transfers set created_at=current_timestamp-interval '20 minutes',expires_at=current_timestamp-interval '10 minutes' where id=?",id);
    assertEquals(409,http("POST",PATH+"/"+id+"/confirm",confirmBody(PASSWORD),liveToken(recipient)).statusCode());
  }
  @Test void twoConcurrentApprovalsCannotDuplicateRootGovernance() throws Exception {
    var first=user(); var second=user(); var sourceJwt=liveToken(reviewer);
    var a=approval(first,sourceJwt); var c=approval(second,sourceJwt);
    var firstJwt=liveToken(first); var secondJwt=liveToken(second);
    var barrier=new CyclicBarrier(2); var pool=Executors.newFixedThreadPool(2);
    try {
      var one=pool.submit(() -> { barrier.await(10,TimeUnit.SECONDS); return http("POST",PATH+"/"+a+"/confirm",confirmBody(PASSWORD),firstJwt).statusCode(); });
      var two=pool.submit(() -> { barrier.await(10,TimeUnit.SECONDS); return http("POST",PATH+"/"+c+"/confirm",confirmBody(PASSWORD),secondJwt).statusCode(); });
      assertEquals(Set.of(200,403),Set.of(one.get(30,TimeUnit.SECONDS),two.get(30,TimeUnit.SECONDS)));
      assertEquals(1,jdbc.queryForObject("select count(*) from authorization_identity_roles a join authorization_roles r on r.id=a.role_id where r.name='ROOT' and a.active",Integer.class));
    } finally { pool.shutdownNow(); }
  }
  @Test void lastRootCannotBeDisabledOrReplacedByAnUnactivatedInvitationAndAttemptsAreAudited() throws Exception {
    var pending=identities.save(service(UUID.randomUUID()+"@example.test"));
    long root=jdbc.queryForObject("select id from authorization_roles where name='ROOT'",Long.class);
    jdbc.update("insert into authorization_identity_roles(identity_id,role_id,authorization_scope_id) values(?,?,?)",pending.getId().value(),root,PLATFORM);
    var result=http("POST","/api/v1/admin/identities/"+reviewer.getId().value()+"/disable",null,liveToken(reviewer));
    assertEquals(409,result.statusCode(),result.body());
    assertTrue(jdbc.queryForObject("select count(*) from authorization_security_events where event='ADMINISTRATIVE_REJECTED' and actor_identity_id=? and reason_code='HTTP_409'",Integer.class,reviewer.getId().value())>0);
  }
  @Test void lastActiveRootCannotBeDemotedToPendingThroughAPersistedLifecycleWrite() {
    assertThrows(org.springframework.dao.DataIntegrityViolationException.class,() ->
        jdbc.update("update ima_identities set status='PENDING' where id=?",reviewer.getId().value()));
    assertEquals("ACTIVE",jdbc.queryForObject("select status from ima_identities where id=?",String.class,reviewer.getId().value()));
  }
  @Test void rootStillRespectsCommonDenyAndCannotBlockTheRecoveryEnvelope() throws Exception {
    var scope=scopes.tenantScope(tenant.getId().value()).orElseThrow();
    denies.deny("TENANT_READ",scope,reviewer.getId().value(),null,REASON);
    assertEquals(403,http("GET","/api/v1/tenants/"+tenant.getId().value(),null,liveToken(reviewer)).statusCode());
    assertThrows(SecurityException.class,() -> denies.deny("AUTHORIZATION_ROOT_RECOVERY",scope,reviewer.getId().value(),null,REASON));
    assertThrows(SecurityException.class,() -> denies.deny("AUTHENTICATION_SERVICE_MANAGE",scope,reviewer.getId().value(),null,REASON));
    assertThrows(SecurityException.class,() -> denies.deny("AUTHORIZATION_AUDIT_READ",scope,reviewer.getId().value(),null,REASON));
  }
  @Test void legitimateRecoveryPreservesCommonDenyInsteadOfBypassingItForTheNewRoot() throws Exception {
    var recipient=user(); var scope=scopes.tenantScope(tenant.getId().value()).orElseThrow();
    denies.deny("TENANT_READ",scope,reviewer.getId().value(),null,REASON);
    var id=approval(recipient,liveToken(reviewer)); var recipientJwt=liveToken(recipient);
    assertEquals(200,http("GET",PATH+"/"+id,null,recipientJwt).statusCode());
    var result=http("POST",PATH+"/"+id+"/confirm",confirmBody(PASSWORD),recipientJwt);
    assertEquals(200,result.statusCode(),result.body());
    assertTrue(decisions.evaluate(recipient.getId().value(),"AUTHORIZATION_ROOT_RECOVERY",PLATFORM).allowed());
    assertFalse(decisions.evaluate(recipient.getId().value(),"TENANT_READ",scope).allowed());
    assertEquals(1,jdbc.queryForObject("select count(*) from authorization_audit where action='ROOT_CONSTRAINT_PRESERVED' and scope_id=?",Integer.class,scope));
  }
  @Test void transferCannotRemoveARootRoleDenyForAPermissionOutsideRootComposition() throws Exception {
    var recipient=user(); var scope=scopes.tenantScope(tenant.getId().value()).orElseThrow();
    var owner="review-"+UUID.randomUUID().toString().replace("-",""); var permission=owner+".restricted";
    jdbc.update("insert into authorization_catalog_owners(service_code,namespace,updated_at) values(?,?,current_timestamp)",owner,owner);
    jdbc.update("insert into authorization_permissions(code,description,module,service_code,namespace,catalog_version,updated_at) values(?,'Review restriction','review',?,?,1,current_timestamp)",permission,owner,owner);
    denies.bootstrapCatalogAuthority(permission,PLATFORM,REASON);
    var independent=roles.create("Preexisting grant "+UUID.randomUUID(),br.com.imarui.ima.authorization.core.domain.model.Role.Ownership.GLOBAL,null,null,Set.of(permission),REASON);
    // Existing legitimate grant; ordinary APIs deliberately forbid self-assignment.
    jdbc.update("insert into authorization_identity_roles(identity_id,role_id,authorization_scope_id) values(?,?,?)",reviewer.getId().value(),independent.id(),PLATFORM);
    long root=jdbc.queryForObject("select id from authorization_roles where name='ROOT'",Long.class);
    assertEquals(0,jdbc.queryForObject("select count(*) from authorization_role_permissions rp join authorization_permissions p on p.id=rp.permission_id where rp.role_id=? and p.code=?",Integer.class,root,permission));
    denies.deny(permission,scope,null,root,REASON);
    assertFalse(decisions.evaluate(reviewer.getId().value(),permission,scope).allowed());
    var id=approval(recipient,liveToken(reviewer));
    var result=http("POST",PATH+"/"+id+"/confirm",confirmBody(PASSWORD),liveToken(recipient));
    assertEquals(200,result.statusCode(),result.body());
    assertFalse(decisions.evaluate(reviewer.getId().value(),permission,scope).allowed(),"Shedding ROOT must not shed its DENY on unrelated Role grants");
    assertEquals(1,jdbc.queryForObject("select count(*) from authorization_denies where identity_id=? and permission_code=? and scope_id=? and active",Integer.class,reviewer.getId().value(),permission,scope));
  }
  @Test void independentlyScopedRootCanTransferItsOwnRealmWithoutObtainingPlatformAuthority() throws Exception {
    var recipient=user(); var next=user();
    var realm=hierarchy.create("REALM_"+UUID.randomUUID().toString().replace("-","").toUpperCase(),"Independent realm",null,REASON);
    var inactive=hierarchy.create("INACTIVE_"+UUID.randomUUID().toString().replace("-","").toUpperCase(),"Inactive resource branch",realm.id(),REASON);
    hierarchy.revise(inactive.id(),inactive.name(),AuthorizationScope.Status.DISABLED,realm.id(),REASON);
    var response=http("POST",PATH,json.writeValueAsString(new RootRecoveryController.Initiate(recipient.getId().value(),realm.id(),PASSWORD,"Realm governance",UUID.randomUUID())),liveToken(reviewer));
    assertEquals(200,response.statusCode(),response.body()); var id=json.readTree(response.body()).path("id").asText();
    var first=http("POST",PATH+"/"+id+"/confirm",confirmBody(PASSWORD),liveToken(recipient));
    assertEquals(200,first.statusCode(),first.body());
    assertFalse(decisions.evaluate(recipient.getId().value(),"AUTHORIZATION_ROLE_MANAGE",PLATFORM).allowed());
    var second=http("POST",PATH,json.writeValueAsString(new RootRecoveryController.Initiate(next.getId().value(),realm.id(),PASSWORD,"Next realm custodian",UUID.randomUUID())),liveToken(recipient));
    assertEquals(200,second.statusCode(),second.body()); var nextId=json.readTree(second.body()).path("id").asText();
    var finalResult=http("POST",PATH+"/"+nextId+"/confirm",confirmBody(PASSWORD),liveToken(next));
    assertEquals(200,finalResult.statusCode(),finalResult.body());
    assertTrue(decisions.evaluate(next.getId().value(),"AUTHORIZATION_ROOT_RECOVERY",realm.id()).allowed());
    assertFalse(decisions.evaluate(next.getId().value(),"AUTHORIZATION_ROOT_RECOVERY",PLATFORM).allowed());
  }
}
