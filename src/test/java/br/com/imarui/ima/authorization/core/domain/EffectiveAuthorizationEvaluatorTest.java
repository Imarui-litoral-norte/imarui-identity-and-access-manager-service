package br.com.imarui.ima.authorization.core.domain;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.domain.policy.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class EffectiveAuthorizationEvaluatorTest {
  private static final Instant NOW=Instant.parse("2026-10-22T12:00:00Z");
  private static final String READ="fleet.read";
  private final UUID actor=UUID.randomUUID(), tenant=UUID.randomUUID();
  private final AuthorizationScope root=AuthorizationScope.create("ROOT","Root",null,NOW);
  private final AuthorizationScope region=AuthorizationScope.create("REGION","Region",root.id(),NOW);
  private final AuthorizationScope local=AuthorizationScope.create("LOCAL","Local",region.id(),NOW);
  private final AuthorizationScope sibling=AuthorizationScope.create("SIBLING","Sibling",region.id(),NOW);
  private final AuthorizationScope other=AuthorizationScope.create("OTHER","Independent",null,NOW);
  private final EffectiveAuthorizationEvaluator evaluator=new EffectiveAuthorizationEvaluator();

  private final class Policy {
    List<AuthorizationScope> scopes=List.of(root,region,local,sibling,other);
    Map<Long,Role> roles=new HashMap<>(Map.of(
        1L,new Role(1L,"GLOBAL",Role.Ownership.GLOBAL,null,null,true,NOW,NOW,0),
        2L,new Role(2L,"REGIONAL",Role.Ownership.SCOPE,region.id(),null,true,NOW,NOW,0),
        3L,new Role(3L,"LOCAL",Role.Ownership.TENANT,local.id(),tenant,true,NOW,NOW,0)));
    Map<Long,Set<String>> permissions=new HashMap<>(Map.of(1L,Set.of(READ),2L,Set.of(READ),3L,Set.of(READ)));
    List<RoleAssignment> assignments=new ArrayList<>(); List<DenyRule> denies=new ArrayList<>();
    List<PermissionDelegation> delegations=new ArrayList<>(); Set<UUID> identities=Set.of(actor), tenants=Set.of(tenant);
    Map<String,Boolean> catalog=Map.of(READ,false);
    AuthorizationPolicyState state() { return new AuthorizationPolicyState(scopes,roles,permissions,assignments,denies,
        delegations,catalog,Map.of(tenant,local.id()),identities,tenants); }
    void assign(long role,UUID scope) { assignments.add(new RoleAssignment(UUID.randomUUID(),actor,role,scope,true,NOW,NOW,0)); }
    void deny(UUID identity,Long role,UUID scope) { denies.add(new DenyRule(UUID.randomUUID(),READ,scope,identity,role,true,"Security decision",NOW,NOW,0)); }
    boolean allowed(UUID scope) { return evaluator.evaluate(state(),actor,READ,scope).allowed(); }
  }

  @Test void availableRoleWithoutAssignmentDoesNotGrantAccess() {
    assertFalse(new Policy().allowed(local.id()));
  }
  @Test void globalRoleAssignmentIsStillBoundToItsScopeAndDescendants() {
    var p=new Policy(); p.assign(1,local.id());
    assertTrue(p.allowed(local.id())); assertFalse(p.allowed(region.id()));
    assertFalse(p.allowed(sibling.id())); assertFalse(p.allowed(other.id()));
  }
  @Test void ancestorAssignmentWorksWithoutAnyAffiliation() {
    var p=new Policy(); p.assign(1,root.id());
    assertTrue(p.allowed(root.id())); assertTrue(p.allowed(local.id())); assertTrue(p.allowed(sibling.id()));
    assertFalse(p.allowed(other.id()));
  }
  @Test void scopeOwnershipDoesNotMakeRoleAvailableOutsideItsSubtree() {
    var p=new Policy(); p.assign(2,region.id());
    assertTrue(p.allowed(local.id())); assertTrue(p.allowed(sibling.id())); assertFalse(p.allowed(root.id()));
    p.assign(2,other.id()); assertFalse(p.allowed(other.id()));
  }
  @Test void tenantRoleCannotBeAppliedToAnotherScopeEvenWithAnInvalidAssignment() {
    var p=new Policy(); p.assign(3,local.id()); p.assign(3,sibling.id());
    assertTrue(p.allowed(local.id())); assertFalse(p.allowed(sibling.id()));
  }
  @Test void inheritedIdentityDenyBeatsSpecificAllowAndOtherRoles() {
    var p=new Policy(); p.assign(1,root.id()); p.assign(2,local.id());
    p.deny(actor,null,region.id());
    assertFalse(p.allowed(local.id())); assertFalse(p.allowed(sibling.id())); assertTrue(p.allowed(root.id()));
    assertEquals(EffectiveAuthorizationEvaluator.Reason.EXPLICIT_DENY,evaluator.evaluate(p.state(),actor,READ,local.id()).reason());
  }
  @Test void roleDenyRequiresThatRoleToBeEffectiveButBeatsOtherRolesWhenItIs() {
    var p=new Policy(); p.assign(1,root.id()); p.deny(null,2L,root.id());
    assertTrue(p.allowed(local.id())); p.assign(2,region.id()); assertFalse(p.allowed(local.id()));
    assertTrue(p.allowed(root.id()));
  }
  @Test void localDenyCannotAffectAncestorsSiblingsOrOtherIdentities() {
    var p=new Policy(); p.assign(1,root.id()); p.deny(actor,null,local.id());
    assertFalse(p.allowed(local.id())); assertTrue(p.allowed(region.id())); assertTrue(p.allowed(sibling.id()));
    var otherIdentity=UUID.randomUUID(); p.identities=Set.of(actor,otherIdentity);
    p.assignments.add(new RoleAssignment(UUID.randomUUID(),otherIdentity,1,root.id(),true,NOW,NOW,0));
    assertTrue(evaluator.evaluate(p.state(),otherIdentity,READ,local.id()).allowed());
  }
  @Test void rootRoleHasNoDenyBypass() {
    var p=new Policy(); p.roles.put(1L,new Role(1L,"ROOT",Role.Ownership.GLOBAL,null,null,true,NOW,NOW,0));
    p.assign(1,root.id()); p.deny(actor,null,root.id()); assertFalse(p.allowed(local.id()));
  }
  @Test void sharedRoleChangesAndRevocationsApplyWithoutChangingAssignments() {
    var p=new Policy(); p.assign(1,root.id()); assertTrue(p.allowed(local.id()));
    var assignments=List.copyOf(p.assignments); p.permissions.put(1L,Set.of());
    assertFalse(p.allowed(local.id())); assertEquals(assignments,p.assignments);
    p.permissions.put(1L,Set.of(READ)); p.roles.put(1L,new Role(1L,"GLOBAL",Role.Ownership.GLOBAL,null,null,false,NOW,NOW,1));
    assertFalse(p.allowed(local.id()));
  }
  @Test void inactiveSubjectsTenantsAndAncestorsFailClosed() {
    var p=new Policy(); p.assign(1,root.id()); p.identities=Set.of(); assertFalse(p.allowed(local.id()));
    p.identities=Set.of(actor); p.tenants=Set.of(); assertFalse(p.allowed(local.id())); assertTrue(p.allowed(sibling.id()));
    p.tenants=Set.of(tenant); p.scopes=List.of(root,region.revise("Region",AuthorizationScope.Status.DISABLED,root.id(),NOW),local,sibling,other);
    assertFalse(p.allowed(local.id()));
  }
  @Test void permissionUseDoesNotImplyDelegationAndDelegationDoesNotImplyUse() {
    var p=new Policy(); p.assign(1,root.id()); assertTrue(p.allowed(local.id()));
    assertFalse(evaluator.mayDelegate(p.state(),actor,READ,local.id(),false));
    p.assignments.clear(); p.delegations.add(new PermissionDelegation(UUID.randomUUID(),actor,READ,region.id(),false,true,"Approved",NOW,NOW,0));
    assertTrue(evaluator.mayDelegate(p.state(),actor,READ,local.id(),false)); assertFalse(p.allowed(local.id()));
    assertFalse(evaluator.mayDelegate(p.state(),actor,READ,local.id(),true));
    assertFalse(evaluator.mayDelegate(p.state(),actor,READ,root.id(),false));
    p.deny(actor,null,region.id()); assertFalse(evaluator.mayDelegate(p.state(),actor,READ,local.id(),false));
  }
  @Test void deprecatedPermissionRemainsUsableByExistingGrantButCannotBeDelegatedAgain() {
    var p=new Policy(); p.assign(1,root.id()); p.catalog=Map.of(READ,true);
    p.delegations.add(new PermissionDelegation(UUID.randomUUID(),actor,READ,root.id(),true,true,"Approved",NOW,NOW,0));
    assertTrue(p.allowed(local.id())); assertFalse(evaluator.mayDelegate(p.state(),actor,READ,local.id(),false));
  }
  @Test void reparentingChangesInheritanceAndMalformedOrMissingResourcesDeny() {
    var p=new Policy(); p.assign(1,root.id()); assertTrue(p.allowed(local.id()));
    p.scopes=List.of(root,region,local.revise("Local",local.status(),other.id(),NOW),sibling,other);
    assertFalse(p.allowed(local.id())); assertTrue(p.allowed(sibling.id()));
    assertFalse(p.allowed(UUID.randomUUID())); assertFalse(evaluator.evaluate(p.state(),null,READ,local.id()).allowed());
    assertFalse(evaluator.evaluate(p.state(),actor,"unknown",local.id()).allowed());
  }
}
