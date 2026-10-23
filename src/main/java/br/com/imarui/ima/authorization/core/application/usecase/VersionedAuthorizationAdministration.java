package br.com.imarui.ima.authorization.core.application.usecase;

import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.OptimisticLockingFailureException;

/** HTTP preconditions are checked inside the same lock as the existing policy use cases. */
@Service
@RequiredArgsConstructor
@Transactional(timeout=10)
public class VersionedAuthorizationAdministration {
  private final AuthorizationMutationRepository mutations;
  private final AuthorizationPolicyRepository policies;
  private final RoleAdministrationUseCases roles;
  private final ScopeAdministrationUseCases scopes;
  private final DenyDelegationUseCases rules;
  private final br.com.imarui.ima.authorization.core.application.service.AuthorizationMutationGuard guard;

  public AuthorizationScope reviseScope(UUID id,String name,AuthorizationScope.Status status,UUID parent,long version,PolicyChangeReason reason) {
    var previous=scopeContext(id,reason).before().scopes().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    version(previous.version(),version); return scopes.revise(id,name,status,parent,reason);
  }
  public AuthorizationScope reparent(UUID id,UUID parent,long version,PolicyChangeReason reason) {
    var previous=scopeContext(id,reason).before().scopes().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    version(previous.version(),version); return scopes.revise(id,previous.name(),previous.status(),parent,reason);
  }
  public void deleteScope(UUID id,long version,PolicyChangeReason reason) {
    var previous=scopeContext(id,reason).before().scopes().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    version(previous.version(),version); scopes.delete(id,reason);
  }
  public Role reviseRole(long id,String name,Role.Status status,Set<String> permissions,long version,PolicyChangeReason reason) {
    var role=roleContext(id,reason).before().roles().get(id); version(role.version(),version);
    return roles.revise(id,name,status,permissions,reason);
  }
  public Role lifecycle(long id,Role.Status status,long version,PolicyChangeReason reason) {
    var state=roleContext(id,reason).before(); var role=state.roles().get(id);
    if(role==null) throw new NoSuchElementException("Role not found.");
    version(role.version(),version); return roles.revise(id,role.name(),status,state.rolePermissions().getOrDefault(id,Set.of()),reason);
  }
  public Role permissions(long id,Set<String> permissions,long version,PolicyChangeReason reason) {
    var role=roleContext(id,reason).before().roles().get(id); version(role.version(),version);
    return roles.revise(id,role.name(),role.status(),permissions,reason);
  }
  public void deleteRole(long id,long version,PolicyChangeReason reason) { var role=roleContext(id,reason).before().roles().get(id); version(role.version(),version); roles.delete(id,reason); }
  public void revokeAssignment(UUID id,long version,PolicyChangeReason reason) {
    var state=guard.begin("AUTHORIZATION_ASSIGNMENT_MANAGE",s -> s.assignments().stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow(() -> new NoSuchElementException("Assignment not found.")).scopeId(),reason).before();
    var previous=state.assignments().stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    version(previous.version(),version); roles.revokeAssignment(id,reason);
  }
  public void revokeDeny(UUID id,long version,PolicyChangeReason reason) {
    var state=guard.begin("AUTHORIZATION_DENY_MANAGE",s -> s.denies().stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow(() -> new NoSuchElementException("DENY not found.")).scopeId(),reason).before();
    var previous=state.denies().stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    version(previous.version(),version); rules.revokeDeny(id,reason);
  }
  public void revokeDelegation(UUID id,long version,PolicyChangeReason reason) {
    var state=guard.begin("AUTHORIZATION_DELEGATION_MANAGE",s -> s.delegations().stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow(() -> new NoSuchElementException("Delegation not found.")).scopeId(),reason).before();
    var previous=state.delegations().stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    version(previous.version(),version); rules.revokeDelegation(id,reason);
  }
  private br.com.imarui.ima.authorization.core.application.service.AuthorizationMutationGuard.Context roleContext(long id,PolicyChangeReason reason) {
    return guard.begin("AUTHORIZATION_ROLE_MANAGE",s -> {
      var role=s.roles().get(id); if(role==null) throw new NoSuchElementException("Role not found.");
      return role.ownership()==Role.Ownership.GLOBAL?AuthorizationScope.PLATFORM_ID:role.ownerScopeId();
    },reason);
  }
  private br.com.imarui.ima.authorization.core.application.service.AuthorizationMutationGuard.Context scopeContext(UUID id,PolicyChangeReason reason) {
    var context=guard.begin("AUTHORIZATION_SCOPE_MANAGE",s -> {
      if(s.scopes().stream().noneMatch(scope -> scope.id().equals(id))) throw new NoSuchElementException("Scope not found.");
      var tree=new br.com.imarui.ima.authorization.core.domain.policy.ScopeHierarchy(s.scopes());
      return tree.ancestors(id).stream().filter(a -> tree.active(a.id())).map(AuthorizationScope::id).findFirst().orElse(AuthorizationScope.PLATFORM_ID);
    },reason);
    guard.requirePotential(context.before(),context.actor(),"AUTHORIZATION_SCOPE_MANAGE",id); return context;
  }
  private void version(long actual,long expected) { if(actual!=expected) throw new OptimisticLockingFailureException("Policy version conflict."); }
}
