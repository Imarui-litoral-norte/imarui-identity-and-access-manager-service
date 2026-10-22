package br.com.imarui.ima.authorization.core.application.usecase;

import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.application.service.AuthorizationMutationGuard;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.domain.policy.*;
import br.com.imarui.ima.authorization.core.repository.*;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ScopeAdministrationUseCases {
  private final ScopeRepository scopes;
  private final AuthorizationMutationGuard guard;
  private final AuthorizationMutationRepository mutations;
  private final AuthorizationPolicyRepository policies;
  private final Clock clock;

  public AuthorizationScope create(String code,String name,UUID parent,PolicyChangeReason reason) {
    UUID origin=parent==null?AuthorizationScope.PLATFORM_ID:parent;
    var context=guard.begin("AUTHORIZATION_SCOPE_MANAGE",origin,reason);
    var hierarchy=new ScopeHierarchy(context.before().scopes());
    if (parent!=null && !hierarchy.active(parent)) throw new SecurityException("Parent Scope is inactive.");
    var now=clock.instant(); var scope=AuthorizationScope.create(code,name,parent,now);
    if (parent==null) {
      // Creating an independent administrative root is an explicit central governance operation.
      // It may extend only the existing ROOT identity's already usable and redelegable capabilities.
      if (!guard.rootIdentity(context.before(),context.actor())) throw new SecurityException("Independent roots require an existing ROOT operator.");
      var root=context.before().roles().values().stream().filter(r -> r.name().equals("ROOT")).findFirst().orElseThrow();
      var rights=context.before().rolePermissions().getOrDefault(root.id(),Set.of());
      for (var permission:rights) {
        guard.require(context.before(),context.actor(),permission,AuthorizationScope.PLATFORM_ID);
        guard.delegate(context.before(),context.actor(),permission,AuthorizationScope.PLATFORM_ID,true);
      }
      scopes.save(scope);
      var initialAssignment=new RoleAssignment(UUID.randomUUID(),context.actor(),root.id(),scope.id(),true,now,now,0);
      mutations.saveAssignment(initialAssignment);
      mutations.audit(context.actor(),"ROOT_SCOPE_GOVERNANCE_INITIALIZED","ROLE_ASSIGNMENT",initialAssignment.id().toString(),scope.id(),reason,null,initialAssignment,now);
      for (var permission:rights) {
        var delegation=new PermissionDelegation(UUID.randomUUID(),context.actor(),permission,scope.id(),true,true,reason.reason(),now,now,0);
        mutations.saveDelegation(delegation);
        mutations.audit(context.actor(),"PERMISSION_DELEGATED","PERMISSION_DELEGATION",delegation.id().toString(),scope.id(),reason,null,delegation,now);
      }
    } else scopes.save(scope);
    var after=policies.current();
    guard.assertGains(context,after,Set.of(scope.id()),after.deprecatedPermissions().keySet(),Map.of(scope.id(),origin));
    mutations.audit(context.actor(),"SCOPE_CREATED","AUTHORIZATION_SCOPE",scope.id().toString(),scope.id(),reason,null,scope,now);
    return scope;
  }

  public AuthorizationScope revise(UUID id,String name,AuthorizationScope.Status status,UUID parent,PolicyChangeReason reason) {
    var context=guard.begin("AUTHORIZATION_SCOPE_MANAGE",s -> managementBoundary(s,id),reason);
    var previous=scope(context.before(),id);
    // Ancestor authority alone must not bypass a DENY placed at the disabled resource itself.
    guard.requirePotential(context.before(),context.actor(),"AUTHORIZATION_SCOPE_MANAGE",id);
    var hierarchy=new ScopeHierarchy(context.before().scopes()); hierarchy.validateParent(id,parent);
    if (!Objects.equals(previous.parentId(),parent)) {
      guard.require(context.before(),context.actor(),"AUTHORIZATION_SCOPE_MANAGE",parent==null?AuthorizationScope.PLATFORM_ID:parent);
      if (parent==null && context.before().assignments().stream().noneMatch(a -> a.active() && a.scopeId().equals(id)
          && context.before().roles().containsKey(a.roleId()) && context.before().roles().get(a.roleId()).name().equals("ROOT")))
        throw new IllegalArgumentException("Detaching a Scope would remove its recovery governance; create an administered root first.");
    }
    var now=clock.instant(); var changed=previous.revise(name,status,parent,now);
    scopes.save(changed);
    var after=policies.current();
    var afterHierarchy=new ScopeHierarchy(after.scopes());
    var movedScopes=guard.descendants(after,id);
    for (var assignment:context.before().assignments()) {
      if (!assignment.active() || !movedScopes.contains(assignment.scopeId())) continue;
      var role=context.before().roles().get(assignment.roleId());
      if (role!=null && role.availableAt(assignment.scopeId(),hierarchy,context.before().tenantScopes())
          && !role.availableAt(assignment.scopeId(),afterHierarchy,after.tenantScopes()))
        throw new IllegalArgumentException("Scope move would invalidate an existing direct RoleAssignment.");
    }
    guard.assertGains(context,after,movedScopes,after.deprecatedPermissions().keySet(),Map.of());
    mutations.audit(context.actor(),"SCOPE_REVISED","AUTHORIZATION_SCOPE",id.toString(),id,reason,previous,changed,now);
    return changed;
  }

  private AuthorizationScope scope(AuthorizationPolicyState state,UUID id) {
    return state.scopes().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow(() -> new NoSuchElementException("Scope not found."));
  }
  private UUID managementBoundary(AuthorizationPolicyState state,UUID id) {
    var hierarchy=new ScopeHierarchy(state.scopes());
    return hierarchy.ancestors(id).stream().filter(s -> hierarchy.active(s.id())).map(AuthorizationScope::id)
        .findFirst().orElse(AuthorizationScope.PLATFORM_ID);
  }
}
