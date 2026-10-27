package br.com.imarui.ima.authorization.infra;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.application.usecase.*;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.repository.ScopeRepository;
import br.com.imarui.ima.identity.core.domain.model.identity.Identity;
import br.com.imarui.ima.identity.core.domain.model.tenant.*;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

class AuthorizationAdministrationIntegrationTest extends IdentityIntegrationSupport {
  @Autowired RoleAdministrationUseCases roles;
  @Autowired ScopeAdministrationUseCases hierarchy;
  @Autowired DenyDelegationUseCases rules;
  @Autowired EffectiveAuthorizationUseCases decisions;
  @Autowired ScopeRepository scopes;
  @Autowired CatalogOwnerAdministrationUseCases catalogOwners;
  @Autowired br.com.imarui.ima.authorization.core.repository.PermissionCatalogRepository catalogRepository;
  @Autowired java.time.Clock clock;
  @Autowired br.com.imarui.ima.authorization.core.repository.AuthorizationPolicyRepository policy;
  private static final UUID PLATFORM=AuthorizationScope.PLATFORM_ID;
  private static final PolicyChangeReason REASON=new PolicyChangeReason("Authorized policy maintenance",UUID.fromString("00000000-0000-0000-0000-000000000081"));

  @BeforeEach void rootOperator() {
    new TransactionTemplate(transactions).executeWithoutResult(s -> {
      jdbc.update("delete from authentication_bootstrap where name='initial-root'");
      jdbc.update("insert into authentication_bootstrap values('initial-root',?,current_timestamp)",reviewer.getId().value());
      jdbc.update("delete from authorization_identity_roles where identity_id=?",reviewer.getId().value());
      rootAuthorization.assign(reviewer.getId().value());
    });
    actor(reviewer.getId());
  }
  @AfterEach void clearBootstrapMarker() { jdbc.update("delete from authentication_bootstrap where name='initial-root'"); }
  private String unique(String prefix) { return prefix+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT); }
  private Identity user() { var identity=identities.save(service(unique("user").toLowerCase()+"@example.test")); firstAccess(identity.getId()); return identities.findById(identity.getId()).orElseThrow(); }
  private UUID scope() { return scopes.tenantScope(tenant.getId().value()).orElseThrow(); }
  private Role global(Set<String> permissions) { return roles.create(unique("Role"),Role.Ownership.GLOBAL,null,null,permissions,REASON); }
  private RoleAssignment grant(Identity target,Role role,UUID scope) { return roles.assign(target.getId().value(),role.id(),scope,REASON); }
  private boolean allowed(Identity target,String code,UUID scope) { return decisions.evaluate(target.getId().value(),code,scope).allowed(); }
  private void operator(Identity target,UUID scope,Set<String> capabilities) { grant(target,global(capabilities),scope); }
  private String accessToken(Identity identity) {
    return new TransactionTemplate(transactions).execute(s -> {
      var now=clock.instant();
      var session=authenticationStore.createSession(identity.getId().value(),now);
      return tokenIssuer.issue(identity.getId().value(),session.getPublicId(),now);
    });
  }
  private <T> T as(Identity identity,Callable<T> action) throws Exception {
    actor(identity.getId()); try { return action.call(); } finally { SecurityContextHolder.clearContext(); }
  }
  private String externalPermission() {
    String owner="service-"+UUID.randomUUID().toString().replace("-","");
    assertTrue(catalogOwners.enroll(owner,owner,REASON)); assertFalse(catalogOwners.enroll(owner,owner,REASON));
    String code=owner+".resource.read";
    var manifest=new PermissionManifest(owner,owner,1,List.of(new PermissionManifest.Entry(code,"External read","external",false)));
    var caller=(br.com.imarui.ima.authorization.core.application.port.CatalogServiceAuthentication)() -> owner;
    var synchronization=new PermissionCatalogUseCases(catalogRepository,Optional.of(caller),clock);
    new TransactionTemplate(transactions).executeWithoutResult(s -> synchronization.synchronizeExternal(manifest));
    return code;
  }

  @Test void scopedGrantInheritsWithoutAffiliationAndNeverReachesSiblingsOrAncestors() {
    var target=user(); var region=hierarchy.create(unique("REGION"),"Region",PLATFORM,REASON);
    var sibling=hierarchy.create(unique("SIBLING"),"Sibling",PLATFORM,REASON);
    var local=scopes.find(scope()).orElseThrow();
    hierarchy.revise(local.id(),local.name(),local.status(),region.id(),REASON);
    var role=roles.create("Regional reader",Role.Ownership.SCOPE,region.id(),null,Set.of("IDENTITY_READ"),REASON);
    assertFalse(allowed(target,"IDENTITY_READ",local.id()));
    grant(target,role,region.id());
    assertTrue(allowed(target,"IDENTITY_READ",local.id()));
    assertFalse(allowed(target,"IDENTITY_READ",PLATFORM)); assertFalse(allowed(target,"IDENTITY_READ",sibling.id()));
    assertEquals(0,jdbc.queryForObject("select count(*) from ima_affiliations where identity_id=?",Integer.class,target.getId().value()));
  }

  @Test void tenantOwnershipAllowsSameNameAcrossOwnersAndRejectsCrossTenantAssignments() {
    var target=user(); var now=Instant.now();
    var second=Tenant.create(TenantId.generate(),TenantCode.from(unique("T").substring(0,30)),TenantName.from("Other"),now);
    second.activate(now); second=tenants.save(second);
    var secondScope=scopes.tenantScope(second.getId().value()).orElseThrow();
    var a=roles.create("Supervisor",Role.Ownership.TENANT,scope(),tenant.getId().value(),Set.of("IDENTITY_READ"),REASON);
    var b=roles.create("Supervisor",Role.Ownership.TENANT,secondScope,second.getId().value(),Set.of("TENANT_READ"),REASON);
    assertNotEquals(a.id(),b.id());
    assertThrows(DataIntegrityViolationException.class,() -> roles.create("Supervisor",Role.Ownership.TENANT,scope(),tenant.getId().value(),Set.of(),REASON));
    assertThrows(SecurityException.class,() -> grant(target,b,scope()));
    grant(target,a,scope()); assertTrue(allowed(target,"IDENTITY_READ",scope())); assertFalse(allowed(target,"IDENTITY_READ",secondScope));
  }

  @Test void inheritedDenyOverridesSpecificAndOtherRoleAllowsAndRevocationIsAudited() {
    var target=user(); var broad=global(Set.of("IDENTITY_READ")); var specific=global(Set.of("IDENTITY_READ"));
    grant(target,broad,PLATFORM); grant(target,specific,scope());
    var deny=rules.deny("IDENTITY_READ",PLATFORM,target.getId().value(),null,REASON);
    assertFalse(allowed(target,"IDENTITY_READ",scope()));
    rules.revokeDeny(deny.id(),REASON); assertTrue(allowed(target,"IDENTITY_READ",scope()));
    assertEquals(2,jdbc.queryForObject("select count(*) from authorization_audit where resource_type='DENY' and resource_id=? and correlation_id=?",Integer.class,deny.id().toString(),REASON.correlationId()));
  }

  @Test void roleDenyAppliesOnlyWhenTheRoleIsEffectiveAndCannotBeBypassedByRemovingIt() {
    var target=user(); var restricted=global(Set.of("IDENTITY_READ")); var other=global(Set.of("IDENTITY_READ"));
    var assignment=grant(target,restricted,scope()); grant(target,other,scope());
    rules.deny("IDENTITY_READ",scope(),null,restricted.id(),REASON);
    assertFalse(allowed(target,"IDENTITY_READ",scope()));
    // Root may explicitly restore another identity's permission because it has the exact delegation authority.
    roles.revokeAssignment(assignment.id(),REASON); assertTrue(allowed(target,"IDENTITY_READ",scope()));
  }

  @Test void permissionUseAndRoleManagementWithoutGranularDelegationCannotGrantPermissions() {
    var manager=user(); var recipient=user();
    operator(manager,scope(),Set.of("AUTHORIZATION_ROLE_MANAGE","AUTHORIZATION_ASSIGNMENT_MANAGE","IDENTITY_READ"));
    var role=global(Set.of("IDENTITY_READ")); actor(manager.getId());
    assertTrue(allowed(manager,"IDENTITY_READ",scope()));
    assertThrows(SecurityException.class,() -> roles.create("Forbidden",Role.Ownership.TENANT,scope(),tenant.getId().value(),Set.of("IDENTITY_READ"),REASON));
    assertThrows(SecurityException.class,() -> grant(recipient,role,scope()));
  }

  @Test void explicitDelegationAllowsGrantingButNotSelfGrantOrTransitiveDelegation() {
    var manager=user(); var recipient=user();
    operator(manager,scope(),Set.of("AUTHORIZATION_ROLE_MANAGE","AUTHORIZATION_ASSIGNMENT_MANAGE","AUTHORIZATION_DELEGATION_MANAGE"));
    rules.delegate(manager.getId().value(),"IDENTITY_READ",scope(),false,REASON);
    actor(manager.getId());
    var role=roles.create("Local reader",Role.Ownership.TENANT,scope(),tenant.getId().value(),Set.of("IDENTITY_READ"),REASON);
    grant(recipient,role,scope()); assertTrue(allowed(recipient,"IDENTITY_READ",scope()));
    assertThrows(SecurityException.class,() -> grant(manager,role,scope()));
    assertThrows(SecurityException.class,() -> rules.delegate(recipient.getId().value(),"IDENTITY_READ",scope(),false,REASON));
  }

  @Test void descendantAdministratorCannotEditAnAncestorOwnedSharedRole() {
    var manager=user(); operator(manager,scope(),Set.of("AUTHORIZATION_ROLE_MANAGE"));
    var shared=roles.create("Shared",Role.Ownership.SCOPE,PLATFORM,null,Set.of("IDENTITY_READ"),REASON);
    actor(manager.getId());
    assertThrows(SecurityException.class,() -> roles.revise(shared.id(),shared.name(),true,Set.of(),REASON));
  }

  @Test void sharedRoleChangesApplyImmediatelyAndSelfAssignmentIsRejected() {
    var recipient=user(); var shared=global(Set.of("IDENTITY_READ")); grant(recipient,shared,scope());
    roles.revise(shared.id(),shared.name(),true,Set.of("IDENTITY_READ","TENANT_READ"),REASON);
    assertTrue(allowed(recipient,"TENANT_READ",scope()));
    assertThrows(SecurityException.class,() -> grant(reviewer,shared,scope()));
  }

  @Test void editingASharedRoleAssignedToTheActorCannotCreateAnIndirectSelfGrant() {
    var manager=user();
    var managed=roles.create("Owned management",Role.Ownership.TENANT,scope(),tenant.getId().value(),Set.of("AUTHORIZATION_ROLE_MANAGE","IDENTITY_READ"),REASON);
    grant(manager,managed,scope()); rules.delegate(manager.getId().value(),"TENANT_READ",scope(),false,REASON);
    actor(manager.getId());
    assertThrows(SecurityException.class,() -> roles.revise(managed.id(),managed.name(),true,Set.of("AUTHORIZATION_ROLE_MANAGE","IDENTITY_READ","TENANT_READ"),REASON));
    assertFalse(allowed(manager,"TENANT_READ",scope()));
    assertEquals(0,jdbc.queryForObject("select count(*) from authorization_role_permissions rp join authorization_permissions p on p.id=rp.permission_id where rp.role_id=? and p.code='TENANT_READ'",Integer.class,managed.id()));
  }

  @Test void rootIsSubjectToOrdinaryDenyButRecoveryChangesAndLastIdentityRemovalAreRejected() {
    rules.deny("IDENTITY_READ",scope(),reviewer.getId().value(),null,REASON);
    assertFalse(allowed(reviewer,"IDENTITY_READ",scope()));
    assertThrows(SecurityException.class,() -> rules.deny("AUTHORIZATION_SCOPE_MANAGE",PLATFORM,reviewer.getId().value(),null,REASON));
    assertThrows(SecurityException.class,() -> rules.deny("AUTHORIZATION_DENY_MANAGE",scope(),reviewer.getId().value(),null,REASON));
    assertThrows(DataIntegrityViolationException.class,() -> jdbc.update("update ima_identities set status='DISABLED' where id=?",reviewer.getId().value()));
    assertThrows(DataIntegrityViolationException.class,() -> jdbc.update("delete from authorization_identity_roles where identity_id=?",reviewer.getId().value()));
    assertTrue(allowed(reviewer,"AUTHORIZATION_SCOPE_MANAGE",PLATFORM));
  }

  @Test void independentRootCreationIsExplicitAndDoesNotExtendOtherIdentitiesPlatformGrants() {
    var target=user(); grant(target,global(Set.of("IDENTITY_READ")),PLATFORM);
    var independent=hierarchy.create(unique("INDEPENDENT"),"Independent root",null,REASON);
    assertNull(independent.parentId());
    assertFalse(allowed(target,"IDENTITY_READ",independent.id()));
    assertTrue(allowed(reviewer,"AUTHORIZATION_SCOPE_MANAGE",independent.id()));
  }

  @Test void tenantHttpUsesTheResourceScopeAndTheSameJwtLosesRevokedPermission() throws Exception {
    var reader=user(); var role=global(Set.of("TENANT_READ")); grant(reader,role,scope());
    var token=accessToken(reader);
    assertEquals(200,http("GET","/api/v1/tenants/"+tenant.getId().value(),null,token).statusCode());
    var now=Instant.now();
    var second=Tenant.create(TenantId.generate(),TenantCode.from(unique("B").substring(0,30)),TenantName.from("Other tenant"),now);
    second.activate(now); second=tenants.save(second);
    assertEquals(403,http("GET","/api/v1/tenants/"+second.getId().value()+"?authorizationScopeId="+scope(),null,token).statusCode());
    roles.revise(role.id(),role.name(),true,Set.of(),REASON);
    assertEquals(403,http("GET","/api/v1/tenants/"+tenant.getId().value(),null,token).statusCode());
  }

  @Test void platformRootAuthorityCannotBypassATenantResourceDeny() throws Exception {
    var token=accessToken(reviewer);
    assertEquals(200,http("GET","/api/v1/tenants/"+tenant.getId().value(),null,token).statusCode());
    rules.deny("TENANT_READ",scope(),reviewer.getId().value(),null,REASON);
    assertEquals(403,http("GET","/api/v1/tenants/"+tenant.getId().value(),null,token).statusCode());
    assertTrue(allowed(reviewer,"TENANT_READ",PLATFORM));
  }

  @Test void reparentingCannotRemoveAnInheritedDenyWithoutDelegationAuthority() {
    var target=user(); var manager=user();
    var oldParent=hierarchy.create(unique("OLD"),"Old parent",PLATFORM,REASON);
    var newParent=hierarchy.create(unique("NEW"),"New parent",PLATFORM,REASON);
    var local=scopes.find(scope()).orElseThrow();
    hierarchy.revise(local.id(),local.name(),local.status(),oldParent.id(),REASON);
    grant(target,global(Set.of("IDENTITY_READ")),PLATFORM);
    rules.deny("IDENTITY_READ",oldParent.id(),target.getId().value(),null,REASON);
    operator(manager,PLATFORM,Set.of("AUTHORIZATION_SCOPE_MANAGE")); actor(manager.getId());
    assertThrows(SecurityException.class,() -> hierarchy.revise(local.id(),local.name(),local.status(),newParent.id(),REASON));
    assertEquals(oldParent.id(),scopes.find(local.id()).orElseThrow().parentId());
    assertFalse(allowed(target,"IDENTITY_READ",local.id()));
  }
  @Test void reparentingCannotRestoreDeniedDelegationEvenWhenTheActorHasNoUsageGrant() {
    var manager=user(); var oldParent=hierarchy.create(unique("DENIED"),"Denied region",PLATFORM,REASON);
    var newParent=hierarchy.create(unique("OPEN"),"Other region",PLATFORM,REASON);
    var resource=scopes.find(scope()).orElseThrow();
    hierarchy.revise(resource.id(),resource.name(),resource.status(),oldParent.id(),REASON);
    operator(manager,PLATFORM,Set.of("AUTHORIZATION_SCOPE_MANAGE"));
    rules.delegate(manager.getId().value(),"IDENTITY_READ",PLATFORM,false,REASON);
    rules.deny("IDENTITY_READ",oldParent.id(),manager.getId().value(),null,REASON);
    assertFalse(allowed(manager,"IDENTITY_READ",resource.id()));
    assertFalse(Boolean.TRUE.equals(new TransactionTemplate(transactions).execute(s -> new br.com.imarui.ima.authorization.core.domain.policy.EffectiveAuthorizationEvaluator().mayDelegate(policy.current(),manager.getId().value(),"IDENTITY_READ",resource.id(),false))));
    actor(manager.getId());
    assertThrows(SecurityException.class,() -> hierarchy.revise(resource.id(),resource.name(),resource.status(),newParent.id(),REASON));
    assertEquals(oldParent.id(),scopes.find(resource.id()).orElseThrow().parentId());
    assertFalse(Boolean.TRUE.equals(new TransactionTemplate(transactions).execute(s -> new br.com.imarui.ima.authorization.core.domain.policy.EffectiveAuthorizationEvaluator().mayDelegate(policy.current(),manager.getId().value(),"IDENTITY_READ",resource.id(),false))));
  }
  @Test void denyRevocationCannotRestoreTheActorsDelegationWithoutAnyUsageGrant() {
    var manager=user(); operator(manager,PLATFORM,Set.of("AUTHORIZATION_DENY_MANAGE"));
    rules.delegate(manager.getId().value(),"IDENTITY_READ",PLATFORM,false,REASON);
    var deny=rules.deny("IDENTITY_READ",scope(),manager.getId().value(),null,REASON);
    assertFalse(allowed(manager,"IDENTITY_READ",scope())); actor(manager.getId());
    assertThrows(SecurityException.class,() -> rules.revokeDeny(deny.id(),REASON));
    assertTrue(jdbc.queryForObject("select active from authorization_denies where id=?",Boolean.class,deny.id()));
  }
  @Test void disablingADenyingRoleCannotRestoreTheActorsDelegationWithoutAnyUsageGrant() {
    var manager=user(); operator(manager,PLATFORM,Set.of("AUTHORIZATION_ROLE_MANAGE"));
    var restricted=global(Set.of()); grant(manager,restricted,PLATFORM);
    rules.delegate(manager.getId().value(),"IDENTITY_READ",PLATFORM,false,REASON);
    rules.deny("IDENTITY_READ",scope(),null,restricted.id(),REASON);
    assertFalse(allowed(manager,"IDENTITY_READ",scope())); actor(manager.getId());
    assertThrows(SecurityException.class,() -> roles.revise(restricted.id(),restricted.name(),Role.Status.INACTIVE,Set.of(),REASON));
    assertEquals("ACTIVE",jdbc.queryForObject("select status from authorization_roles where id=?",String.class,restricted.id()));
  }

  @Test void rootCannotEscapeItsOwnOrdinaryDenyByMovingTheResource() {
    var oldParent=hierarchy.create(unique("OLD"),"Old parent",PLATFORM,REASON);
    var newParent=hierarchy.create(unique("NEW"),"New parent",PLATFORM,REASON);
    var local=scopes.find(scope()).orElseThrow();
    hierarchy.revise(local.id(),local.name(),local.status(),oldParent.id(),REASON);
    rules.deny("IDENTITY_READ",oldParent.id(),reviewer.getId().value(),null,REASON);
    assertThrows(SecurityException.class,() -> hierarchy.revise(local.id(),local.name(),local.status(),newParent.id(),REASON));
    assertEquals(oldParent.id(),scopes.find(local.id()).orElseThrow().parentId());
    assertFalse(allowed(reviewer,"IDENTITY_READ",local.id()));
  }

  @Test void sharedRoleEditChecksDelegationAcrossEveryAssignedScope() {
    var manager=user(); var a=user(); var b=user();
    var region=hierarchy.create(unique("REGION"),"Region",PLATFORM,REASON);
    var childA=hierarchy.create(unique("A"),"A",region.id(),REASON);
    var childB=hierarchy.create(unique("B"),"B",region.id(),REASON);
    var shared=roles.create("Shared scoped role",Role.Ownership.SCOPE,region.id(),null,Set.of(),REASON);
    grant(a,shared,childA.id()); grant(b,shared,childB.id());
    operator(manager,region.id(),Set.of("AUTHORIZATION_ROLE_MANAGE"));
    rules.delegate(manager.getId().value(),"IDENTITY_READ",region.id(),false,REASON);
    rules.deny("IDENTITY_READ",childB.id(),manager.getId().value(),null,REASON);
    actor(manager.getId());
    assertThrows(SecurityException.class,() -> roles.revise(shared.id(),shared.name(),true,Set.of("IDENTITY_READ"),REASON));
    assertFalse(allowed(a,"IDENTITY_READ",childA.id())); assertFalse(allowed(b,"IDENTITY_READ",childB.id()));
  }

  @Test void auditWriteFailureRollsBackTheRoleAndItsPermissions() {
    var suffix=UUID.randomUUID().toString().replace("-",""); var function="auth_test_failure_"+suffix;
    var trigger="auth_test_trigger_"+suffix;
    var name=unique("RollbackRole");
    jdbc.execute("create function "+function+"() returns trigger language plpgsql as $$ begin if new.action='ROLE_CREATED' and new.reason='simulate persistence failure' then raise exception 'simulated audit failure'; end if; return new; end $$");
    jdbc.execute("create trigger "+trigger+" before insert on authorization_audit for each row execute function "+function+"()");
    try {
      assertThrows(org.springframework.dao.DataAccessException.class,() -> roles.create(name,Role.Ownership.GLOBAL,null,null,Set.of("IDENTITY_READ"),new PolicyChangeReason("simulate persistence failure",null)));
      assertEquals(0,jdbc.queryForObject("select count(*) from authorization_roles where name=?",Integer.class,name));
    } finally { jdbc.execute("drop trigger "+trigger+" on authorization_audit"); jdbc.execute("drop function "+function+"()"); }
  }

  @Test void reparentingPreservesDirectScopedAssignmentsOrRejectsTheMoveAtomically() {
    var target=user(); var local=scopes.find(scope()).orElseThrow();
    var shared=roles.create("Owner-bound role",Role.Ownership.SCOPE,PLATFORM,null,Set.of("IDENTITY_READ"),REASON);
    var assignment=grant(target,shared,local.id());
    var independent=hierarchy.create(unique("ROOT"),"Other root",null,REASON);
    assertThrows(IllegalArgumentException.class,() -> hierarchy.revise(local.id(),local.name(),local.status(),independent.id(),REASON));
    assertEquals(PLATFORM,scopes.find(local.id()).orElseThrow().parentId());
    assertTrue(allowed(target,"IDENTITY_READ",local.id()));
    assertTrue(jdbc.queryForObject("select active from authorization_identity_roles where id=?",Boolean.class,assignment.id()));
  }

  @Test void concurrentRoleCreationHasOneWinnerAndNoDuplicateOwnerName() throws Exception {
    var pool=Executors.newFixedThreadPool(2); var barrier=new CyclicBarrier(2); var name=unique("ConcurrentRole");
    Callable<Boolean> create=() -> as(reviewer,() -> {
      barrier.await(10,TimeUnit.SECONDS);
      try { roles.create(name,Role.Ownership.TENANT,scope(),tenant.getId().value(),Set.of("IDENTITY_READ"),REASON); return true; }
      catch (DataIntegrityViolationException duplicate) { return false; }
    });
    try {
      var a=pool.submit(create); var b=pool.submit(create);
      assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
      assertEquals(1,jdbc.queryForObject("select count(*) from authorization_roles where name=? and owner_tenant_id=?",Integer.class,name,tenant.getId().value()));
    } finally { pool.shutdownNow(); }
  }

  @Test void concurrentReparentingCannotCreateACycle() throws Exception {
    var a=hierarchy.create(unique("A"),"A",PLATFORM,REASON); var b=hierarchy.create(unique("B"),"B",PLATFORM,REASON);
    var pool=Executors.newFixedThreadPool(2); var barrier=new CyclicBarrier(2);
    try {
      var first=pool.submit(() -> as(reviewer,() -> {
        barrier.await(10,TimeUnit.SECONDS);
        try { hierarchy.revise(a.id(),a.name(),a.status(),b.id(),REASON); return true; }
        catch (IllegalArgumentException cycle) { return false; }
      }));
      var second=pool.submit(() -> as(reviewer,() -> {
        barrier.await(10,TimeUnit.SECONDS);
        try { hierarchy.revise(b.id(),b.name(),b.status(),a.id(),REASON); return true; }
        catch (IllegalArgumentException cycle) { return false; }
      }));
      assertNotEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
      assertDoesNotThrow(() -> new br.com.imarui.ima.authorization.core.domain.policy.ScopeHierarchy(scopes.all()));
    } finally { pool.shutdownNow(); }
  }

  @Test void aGrantWaitingForADelegationRevocationRevalidatesAfterTheCommit() throws Exception {
    var manager=user(); var target=user();
    operator(manager,scope(),Set.of("AUTHORIZATION_ASSIGNMENT_MANAGE"));
    var delegation=rules.delegate(manager.getId().value(),"IDENTITY_READ",scope(),false,REASON);
    var role=global(Set.of("IDENTITY_READ"));
    var revokedButUncommitted=new CountDownLatch(1); var release=new CountDownLatch(1); var attempted=new CountDownLatch(1);
    var pool=Executors.newFixedThreadPool(2);
    try {
      var revoke=pool.submit(() -> as(reviewer,() -> new TransactionTemplate(transactions).execute(s -> {
        rules.revokeDelegation(delegation.id(),REASON); revokedButUncommitted.countDown();
        try { if (!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Test coordination timed out."); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        return true;
      })));
      assertTrue(revokedButUncommitted.await(10,TimeUnit.SECONDS));
      var grant=pool.submit(() -> as(manager,() -> {
        attempted.countDown();
        try { roles.assign(target.getId().value(),role.id(),scope(),REASON); return false; }
        catch (SecurityException denied) { return true; }
      }));
      assertTrue(attempted.await(10,TimeUnit.SECONDS));
      assertThrows(TimeoutException.class,() -> grant.get(150,TimeUnit.MILLISECONDS));
      release.countDown(); assertTrue(revoke.get(20,TimeUnit.SECONDS)); assertTrue(grant.get(20,TimeUnit.SECONDS));
      assertFalse(allowed(target,"IDENTITY_READ",scope()));
    } finally { release.countDown(); pool.shutdownNow(); }
  }

  @Test void twoConcurrentDisablesCannotRemoveBothRecoveryAdministrators() throws Exception {
    var otherRoot=user();
    jdbc.update("insert into authorization_identity_roles(identity_id,role_id,authorization_scope_id) select ?,id,? from authorization_roles where name='ROOT'",otherRoot.getId().value(),PLATFORM);
    var pool=Executors.newFixedThreadPool(2); var barrier=new CyclicBarrier(2);
    java.util.function.Function<Identity,Callable<Boolean>> disabling=identity -> () -> {
      barrier.await(10,TimeUnit.SECONDS);
      try { jdbc.update("update ima_identities set status='DISABLED' where id=?",identity.getId().value()); return true; }
      catch (DataIntegrityViolationException protectedAdministrator) { return false; }
    };
    try {
      var first=pool.submit(disabling.apply(reviewer)); var second=pool.submit(disabling.apply(otherRoot));
      assertNotEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
      var remaining=jdbc.queryForList("select distinct i.id from ima_identities i join authorization_identity_roles a on a.identity_id=i.id join authorization_roles r on r.id=a.role_id where i.status='ACTIVE' and a.active and r.active and r.name='ROOT'",UUID.class);
      assertEquals(1,remaining.size()); assertTrue(decisions.evaluate(remaining.get(0),"AUTHORIZATION_SCOPE_MANAGE",PLATFORM).allowed());
    } finally { pool.shutdownNow(); }
  }

  @Test void initialExternalCatalogAuthorityIsAnExplicitApprovalAndStillDoesNotGrantUsage() {
    var target=user(); var code=externalPermission();
    assertFalse(allowed(reviewer,code,scope()));
    assertEquals(0,jdbc.queryForObject("select count(*) from authorization_permission_delegations where permission_code=?",Integer.class,code));
    assertThrows(SecurityException.class,() -> roles.create("Unapproved external grant",Role.Ownership.SCOPE,scope(),null,Set.of(code),REASON));
    rules.bootstrapCatalogAuthority(code,PLATFORM,REASON);
    assertFalse(allowed(reviewer,code,scope()));
    var role=roles.create("Approved external grant",Role.Ownership.SCOPE,scope(),null,Set.of(code),REASON);
    grant(target,role,scope()); assertTrue(allowed(target,code,scope())); assertFalse(allowed(reviewer,code,scope()));
  }

  @Test void catalogAdministrationAloneCannotCreateInitialDelegationAuthority() {
    var code=externalPermission(); var manager=user();
    operator(manager,PLATFORM,Set.of("AUTHORIZATION_CATALOG_MANAGE","AUTHORIZATION_DELEGATION_MANAGE"));
    actor(manager.getId());
    assertThrows(SecurityException.class,() -> rules.bootstrapCatalogAuthority(code,PLATFORM,REASON));
    assertEquals(0,jdbc.queryForObject("select count(*) from authorization_permission_delegations where permission_code=?",Integer.class,code));
  }

  @Test void initialAuthorityCannotBypassADenyOrResurrectARevokedAuthority() {
    var code=externalPermission();
    var deny=rules.deny(code,PLATFORM,reviewer.getId().value(),null,REASON);
    assertThrows(SecurityException.class,() -> rules.bootstrapCatalogAuthority(code,PLATFORM,REASON));
    // No previous ALLOW exists, so removing this DENY creates no access or self-grant.
    rules.revokeDeny(deny.id(),REASON);
    var initial=rules.bootstrapCatalogAuthority(code,PLATFORM,REASON);
    assertThrows(SecurityException.class,() -> rules.bootstrapCatalogAuthority(code,PLATFORM,REASON));
    jdbc.update("update authorization_permission_delegations set active=false where id=?",initial.id());
    assertThrows(SecurityException.class,() -> rules.bootstrapCatalogAuthority(code,PLATFORM,REASON));
  }

  @Test void externalAuthorityIsApprovedSeparatelyForEachIndependentRoot() {
    var code=externalPermission(); rules.bootstrapCatalogAuthority(code,PLATFORM,REASON);
    var independent=hierarchy.create(unique("ROOT"),"Independent root",null,REASON);
    assertThrows(SecurityException.class,() -> roles.create("Unapproved independent grant",Role.Ownership.SCOPE,independent.id(),null,Set.of(code),REASON));
    rules.bootstrapCatalogAuthority(code,independent.id(),REASON);
    assertNotNull(roles.create("Approved independent grant",Role.Ownership.SCOPE,independent.id(),null,Set.of(code),REASON).id());
    assertFalse(allowed(reviewer,code,independent.id()));
  }

  @Test void detachingAScopeWithoutRecoveryGovernanceIsRejectedWithoutChangingItsUuid() {
    var existing=scopes.find(scope()).orElseThrow();
    assertThrows(IllegalArgumentException.class,() -> hierarchy.revise(existing.id(),existing.name(),existing.status(),null,REASON));
    assertEquals(existing,scopes.find(existing.id()).orElseThrow());
  }

  @Test void identityDeletionArchivesAuthorizationRowsWithTheIdentityHistoryCorrelation() {
    var target=user(); var role=global(Set.of("IDENTITY_READ"));
    var assignment=grant(target,role,scope());
    var deny=rules.deny("TENANT_READ",scope(),target.getId().value(),null,REASON);
    var delegation=rules.delegate(target.getId().value(),"IDENTITY_READ",scope(),false,REASON);
    actor(target.getId());
    delete.execute(new br.com.imarui.ima.identity.core.application.command.identity.SelfDeleteIdentityCommand("Self requested removal"));
    assertTrue(identities.findById(target.getId()).isEmpty());
    var historyId=jdbc.queryForObject("select id from ima_identities__history where original_identity_id=?",UUID.class,target.getId().value());
    assertEquals(3,jdbc.queryForObject("select count(*) from authorization_audit where action='AUTHORIZATION_ROW_REMOVED' and correlation_id=? and actor_identity_id=?",Integer.class,historyId,target.getId().value()));
    for (var id:List.of(assignment.id(),deny.id(),delegation.id()))
      assertEquals(1,jdbc.queryForObject("select count(*) from authorization_audit where action='AUTHORIZATION_ROW_REMOVED' and resource_id=? and correlation_id=? and before_state is not null and after_state is null",Integer.class,id.toString(),historyId));
  }

  @Test void rootCreationAndDefinitionAreProtectedAndHttpDisableReturnsConflictWithoutRevocation() throws Exception {
    var target=user(); long rootRole=jdbc.queryForObject("select id from authorization_roles where name='ROOT'",Long.class);
    assertThrows(SecurityException.class,() -> roles.assign(target.getId().value(),rootRole,PLATFORM,REASON));
    assertThrows(SecurityException.class,() -> roles.create("ROOT",Role.Ownership.GLOBAL,null,null,Set.of(),REASON));
    assertThrows(SecurityException.class,() -> roles.revise(rootRole,"ROOT",false,Set.of(),REASON));
    var token=accessToken(reviewer);
    long revocations=jdbc.queryForObject("select count(*) from ima_access_revocation_requests where subject_id=?",Long.class,reviewer.getId().value());
    var response=http("POST","/api/v1/admin/identities/"+reviewer.getId().value()+"/disable",null,token);
    assertEquals(409,response.statusCode()); assertFalse(response.body().contains("authorization_identity_roles"));
    assertTrue(identities.findById(reviewer.getId()).orElseThrow().isActive());
    assertEquals(revocations,jdbc.queryForObject("select count(*) from ima_access_revocation_requests where subject_id=?",Long.class,reviewer.getId().value()));
    assertEquals(200,http("GET","/api/v1/tenants/"+tenant.getId().value(),null,token).statusCode());
  }

  @Test void ancestorRoleDenyAppliesWhereTheScopeOwnedRoleBecomesEffective() {
    var target=user(); var region=hierarchy.create(unique("REGION"),"Region",PLATFORM,REASON);
    var shared=roles.create("Region capability",Role.Ownership.SCOPE,region.id(),null,Set.of("IDENTITY_READ"),REASON);
    grant(target,shared,region.id()); assertTrue(allowed(target,"IDENTITY_READ",region.id()));
    rules.deny("IDENTITY_READ",PLATFORM,null,shared.id(),REASON);
    assertFalse(allowed(target,"IDENTITY_READ",region.id()));
    assertFalse(allowed(target,"IDENTITY_READ",PLATFORM));
  }

  @Test void databaseCannotInventAPlatformAssignmentOrAnInternalOwnerForAnIncompleteWrite() {
    var target=user(); var role=global(Set.of("IDENTITY_READ"));
    assertThrows(DataIntegrityViolationException.class,() -> jdbc.update("insert into authorization_identity_roles(identity_id,role_id) values(?,?)",target.getId().value(),role.id()));
    assertThrows(DataIntegrityViolationException.class,() -> jdbc.update("insert into authorization_roles(name,active) values(?,true)",unique("MissingOwner")));
    assertThrows(DataIntegrityViolationException.class,() -> jdbc.update("insert into authorization_permissions(code,description,module,updated_at) values(?,'No owner','EXTERNAL',current_timestamp)",unique("MissingOwner")));
    assertEquals(0,jdbc.queryForObject("select count(*) from authorization_identity_roles where identity_id=?",Integer.class,target.getId().value()));
  }

  @Test void aReadCommittedCallerNeverCombinesRoleStateFromDifferentCommittedPolicyRevisions() throws Exception {
    var target=user(); var role=global(Set.of()); grant(target,role,scope());
    var pool=Executors.newFixedThreadPool(2); var barrier=new CyclicBarrier(2);
    try {
      var writer=pool.submit(() -> {
        barrier.await(10,TimeUnit.SECONDS);
        for (int i=0;i<60;i++) {
          new TransactionTemplate(transactions).executeWithoutResult(s -> {
            jdbc.update("update authorization_roles set active=false,updated_at=clock_timestamp(),version=version+1 where id=?",role.id());
            jdbc.update("insert into authorization_role_permissions(role_id,permission_id) select ?,id from authorization_permissions where code='IDENTITY_READ' on conflict do nothing",role.id());
          });
          new TransactionTemplate(transactions).executeWithoutResult(s -> {
            jdbc.update("delete from authorization_role_permissions where role_id=?",role.id());
            jdbc.update("update authorization_roles set active=true,updated_at=clock_timestamp(),version=version+1 where id=?",role.id());
          });
        }
        return true;
      });
      var reader=pool.submit(() -> {
        var outer=new TransactionTemplate(transactions);
        outer.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        return outer.execute(s -> {
          try { barrier.await(10,TimeUnit.SECONDS); }
          catch (Exception e) { throw new IllegalStateException(e); }
          for (int i=0;i<100;i++) assertFalse(allowed(target,"IDENTITY_READ",scope()));
          return true;
        });
      });
      assertTrue(writer.get(30,TimeUnit.SECONDS)); assertTrue(reader.get(30,TimeUnit.SECONDS));
    } finally { pool.shutdownNow(); }
  }

  @Test void equivalentActiveDeniesAreIdempotentAndRevocationCreatesANewHistoricalGeneration() {
    var target=user(); var role=global(Set.of("IDENTITY_READ")); grant(target,role,scope());
    var first=rules.deny("IDENTITY_READ",scope(),target.getId().value(),null,REASON);
    var duplicate=rules.deny("IDENTITY_READ",scope(),target.getId().value(),null,REASON);
    assertEquals(first.id(),duplicate.id()); assertFalse(allowed(target,"IDENTITY_READ",scope()));
    rules.revokeDeny(first.id(),REASON); assertTrue(allowed(target,"IDENTITY_READ",scope()));
    var next=rules.deny("IDENTITY_READ",scope(),target.getId().value(),null,REASON);
    assertNotEquals(first.id(),next.id()); assertFalse(allowed(target,"IDENTITY_READ",scope()));
    assertEquals(2,jdbc.queryForObject("select count(*) from authorization_denies where identity_id=?",Integer.class,target.getId().value()));
  }

  @Test void roleDeniesHaveTheirOwnLogicalUniquenessAndDatabaseBackstop() {
    var role=global(Set.of("IDENTITY_READ"));
    var first=rules.deny("IDENTITY_READ",scope(),null,role.id(),REASON);
    assertEquals(first.id(),rules.deny("IDENTITY_READ",scope(),null,role.id(),REASON).id());
    assertThrows(DataIntegrityViolationException.class,() -> jdbc.update("""
        insert into authorization_denies(id,permission_code,scope_id,role_id,active,reason,created_at,updated_at)
        values(?,'IDENTITY_READ',?,?,true,'duplicate',current_timestamp,current_timestamp)
        """,UUID.randomUUID(),scope(),role.id()));
  }

  @Test void concurrentDuplicateDeniesReturnOneStableId() throws Exception {
    var target=user(); var resource=scope(); var barrier=new CyclicBarrier(2);
    var pool=Executors.newFixedThreadPool(2);
    try {
      Callable<UUID> command=() -> as(reviewer,() -> {
        barrier.await(10,TimeUnit.SECONDS);
        return rules.deny("IDENTITY_READ",resource,target.getId().value(),null,REASON).id();
      });
      var a=pool.submit(command); var c=pool.submit(command);
      assertEquals(a.get(20,TimeUnit.SECONDS),c.get(20,TimeUnit.SECONDS));
      assertEquals(1,jdbc.queryForObject("select count(*) from authorization_denies where active and identity_id=?",Integer.class,target.getId().value()));
    } finally { pool.shutdownNow(); }
  }

  @Test void explicitRoleLifecyclePreservesAssignmentsAndDeniesNewArchivedAssignments() {
    var target=user(); var role=global(Set.of("IDENTITY_READ")); var assignment=grant(target,role,scope());
    var inactive=roles.revise(role.id(),role.name(),Role.Status.INACTIVE,Set.of("IDENTITY_READ"),REASON);
    assertEquals(Role.Status.INACTIVE,inactive.status()); assertFalse(allowed(target,"IDENTITY_READ",scope()));
    roles.revise(role.id(),role.name(),Role.Status.ACTIVE,Set.of("IDENTITY_READ"),REASON);
    assertTrue(allowed(target,"IDENTITY_READ",scope()));
    assertThrows(IllegalStateException.class,() -> roles.revise(role.id(),role.name(),Role.Status.ARCHIVED,Set.of("IDENTITY_READ"),REASON));
    roles.revokeAssignment(assignment.id(),REASON);
    var archived=roles.revise(role.id(),role.name(),Role.Status.ARCHIVED,Set.of("IDENTITY_READ"),REASON);
    assertEquals(Role.Status.ARCHIVED,archived.status()); assertFalse(archived.active());
    assertThrows(SecurityException.class,() -> grant(target,archived,scope()));
    assertThrows(IllegalStateException.class,() -> roles.revise(role.id(),role.name(),Role.Status.ACTIVE,Set.of("IDENTITY_READ"),REASON));
    assertThrows(IllegalStateException.class,() -> roles.delete(role.id(),REASON));
    assertEquals(1,jdbc.queryForObject("select count(*) from authorization_identity_roles where id=?",Integer.class,assignment.id()));
  }

  @Test void roleDeletionRequiresArchiveAndRetainsIndependentHistory() {
    var role=global(Set.of("IDENTITY_READ"));
    assertThrows(IllegalStateException.class,() -> roles.delete(role.id(),REASON));
    roles.revise(role.id(),role.name(),Role.Status.ARCHIVED,Set.of("IDENTITY_READ"),REASON);
    roles.delete(role.id(),REASON);
    assertEquals(0,jdbc.queryForObject("select count(*) from authorization_roles where id=?",Integer.class,role.id()));
    assertEquals(1,jdbc.queryForObject("select count(*) from authorization_roles__history where original_role_id=? and role_state->>'status'='ARCHIVED' and jsonb_array_length(permission_state)=1",Integer.class,role.id()));
    assertEquals(1,jdbc.queryForObject("select count(*) from authorization_audit where action='ROLE_DELETED' and resource_id=?",Integer.class,role.id().toString()));
  }

  @Test void rootLifecycleIsProtectedAtBothDomainAdministrationAndDatabase() {
    long root=jdbc.queryForObject("select id from authorization_roles where name='ROOT'",Long.class);
    assertThrows(RuntimeException.class,() -> roles.revise(root,"ROOT",Role.Status.ARCHIVED,Set.of(),REASON));
    assertThrows(DataIntegrityViolationException.class,() -> jdbc.update("update authorization_roles set status='INACTIVE' where id=?",root));
    assertTrue(allowed(reviewer,"AUTHORIZATION_ROLE_MANAGE",PLATFORM));
  }

  @Test void aLegacyRootLikeLabelDoesNotCreateARootIdentityDuringReconstitution() {
    var code=externalPermission(); var manager=user();
    long legacy=jdbc.queryForObject("insert into authorization_roles(name,ownership,active) values(' ROOT ','GLOBAL',true) returning id",Long.class);
    for (var capability:Set.of("AUTHORIZATION_SCOPE_MANAGE","AUTHORIZATION_CATALOG_MANAGE","AUTHORIZATION_DELEGATION_MANAGE"))
      jdbc.update("insert into authorization_role_permissions(role_id,permission_id) select ?,id from authorization_permissions where code=?",legacy,capability);
    roles.assign(manager.getId().value(),legacy,PLATFORM,REASON);
    actor(manager.getId());
    assertThrows(SecurityException.class,() -> rules.bootstrapCatalogAuthority(code,PLATFORM,REASON));
    assertThrows(SecurityException.class,() -> hierarchy.create(unique("FORBIDDEN"),"Independent",null,REASON));
    actor(reviewer.getId());
    assertThrows(SecurityException.class,() -> roles.create(" ROOT ",Role.Ownership.GLOBAL,null,null,Set.of(),REASON));
    assertEquals(" ROOT ",jdbc.queryForObject("select name from authorization_roles where id=?",String.class,legacy));
  }
}
