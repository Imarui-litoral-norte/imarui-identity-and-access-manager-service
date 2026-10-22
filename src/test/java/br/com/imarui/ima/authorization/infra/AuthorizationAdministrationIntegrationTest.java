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
  private static final UUID PLATFORM=AuthorizationScope.PLATFORM_ID;
  private static final PolicyChangeReason REASON=new PolicyChangeReason("Authorized policy maintenance",UUID.fromString("00000000-0000-0000-0000-000000000081"));

  @BeforeEach void rootOperator() {
    new TransactionTemplate(transactions).executeWithoutResult(s -> {
      jdbc.update("delete from authentication_bootstrap where name='initial-root'");
      jdbc.update("insert into authentication_bootstrap values('initial-root',?,current_timestamp)",reviewer.getId().value());
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
      var session=authenticationStore.createSession(identity.getId().value(),Instant.now());
      return tokenIssuer.issue(identity.getId().value(),session.getPublicId(),Instant.now());
    });
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
}
