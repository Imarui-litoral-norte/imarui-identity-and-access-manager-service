package br.com.imarui.ima.authorization.core.domain.policy;

import java.util.*;

/** Shared internal algorithm. ROOT follows the same DENY precedence as any other Role. */
public final class EffectiveAuthorizationEvaluator {
  public enum Reason { ALLOWED, INACTIVE_IDENTITY, UNKNOWN_PERMISSION, UNAVAILABLE_RESOURCE, EXPLICIT_DENY, NO_GRANT }
  public record Decision(boolean allowed, Reason reason) {}

  public Decision evaluate(AuthorizationPolicyState state, UUID identity, String permission, UUID scope) {
    if (identity==null || permission==null || scope==null) return new Decision(false,Reason.UNAVAILABLE_RESOURCE);
    if (!state.activeIdentities().contains(identity)) return new Decision(false,Reason.INACTIVE_IDENTITY);
    if (!state.deprecatedPermissions().containsKey(permission)) return new Decision(false,Reason.UNKNOWN_PERMISSION);
    try {
      var hierarchy=new ScopeHierarchy(state.scopes());
      if (!state.resourceAvailable(scope,hierarchy)) return new Decision(false,Reason.UNAVAILABLE_RESOURCE);
      var roles=effectiveRoles(state,identity,scope,hierarchy);
      if (denied(state,identity,permission,scope,roles,hierarchy)) return new Decision(false,Reason.EXPLICIT_DENY);
      return roles.stream().anyMatch(id -> state.rolePermissions().getOrDefault(id,Set.of()).contains(permission))
          ? new Decision(true,Reason.ALLOWED) : new Decision(false,Reason.NO_GRANT);
    } catch (IllegalArgumentException corruptOrUnknownScope) {
      return new Decision(false,Reason.UNAVAILABLE_RESOURCE);
    }
  }

  public boolean mayDelegate(AuthorizationPolicyState state, UUID identity, String permission, UUID scope, boolean redelegating) {
    if (identity==null || permission==null || scope==null) return false;
    if (!state.activeIdentities().contains(identity) || !Boolean.FALSE.equals(state.deprecatedPermissions().get(permission))) return false;
    try {
      var hierarchy=new ScopeHierarchy(state.scopes());
      if (!state.resourceAvailable(scope,hierarchy)
          || denied(state,identity,permission,scope,effectiveRoles(state,identity,scope,hierarchy),hierarchy)) return false;
      return state.delegations().stream().anyMatch(d -> d.active() && d.identityId().equals(identity)
          && d.permission().equals(permission) && hierarchy.covers(d.scopeId(),scope)
          && (!redelegating || d.redelegable()));
    } catch (IllegalArgumentException corruptOrUnknownScope) { return false; }
  }

  public Set<Long> effectiveRoles(AuthorizationPolicyState state, UUID identity, UUID scope, ScopeHierarchy hierarchy) {
    var result=new HashSet<Long>();
    for (var assignment:state.assignments()) {
      if (!assignment.active() || !assignment.identityId().equals(identity) || !hierarchy.active(assignment.scopeId())
          || !hierarchy.covers(assignment.scopeId(),scope)) continue;
      var role=state.roles().get(assignment.roleId());
      if (role!=null && role.active() && role.availableAt(assignment.scopeId(),hierarchy,state.tenantScopes())
          && role.availableAt(scope,hierarchy,state.tenantScopes())) result.add(role.id());
    }
    return Set.copyOf(result);
  }

  private boolean denied(AuthorizationPolicyState state, UUID identity, String permission, UUID scope,
      Set<Long> roles, ScopeHierarchy hierarchy) {
    return state.denies().stream().anyMatch(d -> d.active() && d.permission().equals(permission)
        && hierarchy.covers(d.scopeId(),scope)
        && (identity.equals(d.identityId()) || d.roleId()!=null && roles.contains(d.roleId())));
  }
}
