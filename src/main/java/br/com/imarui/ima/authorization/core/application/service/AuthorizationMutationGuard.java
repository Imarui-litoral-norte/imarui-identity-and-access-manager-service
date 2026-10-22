package br.com.imarui.ima.authorization.core.application.service;

import br.com.imarui.ima.authorization.core.application.port.AuthorizationActor;
import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.domain.policy.*;
import br.com.imarui.ima.authorization.core.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** All checks and persistence occur under the same database policy lock and caller transaction. */
@Component
@RequiredArgsConstructor
public class AuthorizationMutationGuard {
  public record Context(UUID actor,AuthorizationPolicyState before) {}
  private final AuthorizationActor actors;
  private final AuthorizationPolicyRepository policies;
  private final AuthorizationMutationRepository mutations;
  private final EffectiveAuthorizationEvaluator evaluator=new EffectiveAuthorizationEvaluator();

  public Context begin(String capability,UUID scope,PolicyChangeReason reason) {
    return begin(capability,state -> scope,reason);
  }

  public Context begin(String capability,java.util.function.Function<AuthorizationPolicyState,UUID> resource,PolicyChangeReason reason) {
    Objects.requireNonNull(reason,"Policy change reason required.");
    mutations.lock();
    UUID actor=actors.requireIdentityId();
    var state=policies.current();
    require(state,actor,capability,resource.apply(state));
    return new Context(actor,state);
  }

  public void require(AuthorizationPolicyState state,UUID actor,String capability,UUID scope) {
    if (!evaluator.evaluate(state,actor,capability,scope).allowed()) throw new SecurityException("Administrative operation denied.");
  }

  public void requirePotential(AuthorizationPolicyState state,UUID actor,String capability,UUID scope) {
    require(potential(state),actor,capability,scope);
  }

  public void delegate(AuthorizationPolicyState state,UUID actor,String permission,UUID scope,boolean redelegating) {
    if (!evaluator.mayDelegate(state,actor,permission,scope,redelegating))
      throw new SecurityException("Explicit Permission delegation authority required.");
    if (centralPermission(permission)) require(state,actor,"AUTHORIZATION_DELEGATION_MANAGE",AuthorizationScope.PLATFORM_ID);
  }

  public boolean centralPermission(String code) {
    return code.startsWith("AUTHORIZATION_") || code.startsWith("AUTHENTICATION_")
        || Set.of("IDENTITY_MANAGE","IDENTITY_APPLICATION_REVIEW","TENANT_MANAGE").contains(code);
  }

  public boolean rootIdentity(AuthorizationPolicyState state,UUID identity) {
    var hierarchy=new ScopeHierarchy(state.scopes());
    return state.assignments().stream().anyMatch(a -> a.active() && a.identityId().equals(identity)
        && state.roles().containsKey(a.roleId()) && state.roles().get(a.roleId()).active()
        && state.roles().get(a.roleId()).name().equals("ROOT") && hierarchy.active(a.scopeId()));
  }

  /** Check all impacted grants, including currently inactive recipients/resources that could reactivate. */
  public void assertGains(Context context,AuthorizationPolicyState after,Set<UUID> affectedScopes,
      Set<String> permissions,Map<UUID,UUID> originsForNewScopes) {
    assertRecovery(after);
    var beforePotential=potential(context.before());
    var afterPotential=potential(after);
    var beforeHierarchy=new ScopeHierarchy(beforePotential.scopes());
    var afterHierarchy=new ScopeHierarchy(afterPotential.scopes());
    var recipients=new HashSet<>(beforePotential.activeIdentities());
    recipients.addAll(afterPotential.activeIdentities());
    for (UUID recipient:recipients) for (UUID scope:affectedScopes) for (String permission:permissions) {
      UUID origin=originsForNewScopes.getOrDefault(scope,scope);
      if (evaluator.evaluate(afterPotential,recipient,permission,scope,afterHierarchy).allowed()
          && !evaluator.evaluate(beforePotential,recipient,permission,origin,beforeHierarchy).allowed()) {
        if (recipient.equals(context.actor())) throw new SecurityException("Self-grant or indirect self-escalation rejected.");
        delegate(context.before(),context.actor(),permission,origin,false);
      }
    }
  }

  public void assertRecovery(AuthorizationPolicyState state) {
    var roots=state.assignments().stream().filter(a -> a.active() && state.roles().containsKey(a.roleId())
        && state.roles().get(a.roleId()).name().equals("ROOT")).toList();
    var eligible=new HashSet<>(state.activeIdentities()); roots.forEach(a -> eligible.add(a.identityId()));
    var recovery=new AuthorizationPolicyState(state.scopes(),state.roles(),state.rolePermissions(),state.assignments(),
        state.denies(),state.delegations(),state.deprecatedPermissions(),state.tenantScopes(),eligible,state.activeTenants());
    var currentHierarchy=new ScopeHierarchy(recovery.scopes());
    for (var root:roots) for (var code:Set.of("AUTHORIZATION_SCOPE_MANAGE","AUTHORIZATION_ROLE_MANAGE",
        "AUTHORIZATION_ASSIGNMENT_MANAGE","AUTHORIZATION_DENY_MANAGE","AUTHORIZATION_DELEGATION_MANAGE","AUTHORIZATION_CATALOG_MANAGE"))
      if (!evaluator.evaluate(recovery,root.identityId(),code,root.scopeId(),currentHierarchy).allowed())
        throw new SecurityException("Protected administrative recovery would be lost.");
    var future=potential(recovery);
    var futureHierarchy=new ScopeHierarchy(future.scopes());
    for (var root:roots) for (var scope:currentHierarchy.descendants(root.scopeId()))
      for (var code:Set.of("AUTHORIZATION_SCOPE_MANAGE","AUTHORIZATION_ROLE_MANAGE","AUTHORIZATION_ASSIGNMENT_MANAGE",
          "AUTHORIZATION_DENY_MANAGE","AUTHORIZATION_DELEGATION_MANAGE","AUTHORIZATION_CATALOG_MANAGE"))
        if (!evaluator.evaluate(future,root.identityId(),code,scope.id(),futureHierarchy).allowed())
          throw new SecurityException("A descendant recovery boundary would become inaccessible.");
  }

  private AuthorizationPolicyState potential(AuthorizationPolicyState state) {
    var recipients=new HashSet<>(state.activeIdentities());
    state.assignments().forEach(a -> recipients.add(a.identityId()));
    var availableScopes=state.scopes().stream().map(s -> new AuthorizationScope(s.id(),s.code(),s.name(),
        AuthorizationScope.Status.ACTIVE,s.parentId(),s.createdAt(),s.updatedAt(),s.version())).toList();
    return new AuthorizationPolicyState(availableScopes,state.roles(),state.rolePermissions(),state.assignments(),
        state.denies(),state.delegations(),state.deprecatedPermissions(),state.tenantScopes(),recipients,state.tenantScopes().keySet());
  }

  public Set<UUID> descendants(AuthorizationPolicyState state,UUID scope) {
    return new HashSet<>(new ScopeHierarchy(state.scopes()).descendants(scope).stream().map(AuthorizationScope::id).toList());
  }
}
