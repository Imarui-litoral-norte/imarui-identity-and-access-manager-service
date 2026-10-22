package br.com.imarui.ima.authorization.core.domain.policy;

import java.util.*;

/** Shared internal algorithm. ROOT follows the same DENY precedence as any other Role. */
public final class EffectiveAuthorizationEvaluator {
  public enum Reason { ALLOWED, INACTIVE_IDENTITY, UNKNOWN_PERMISSION, UNAVAILABLE_RESOURCE, EXPLICIT_DENY, NO_GRANT }
  public record Decision(boolean allowed, Reason reason) {}
  public record Trace(String effect,boolean inherited,Long roleId,UUID originScopeId,UUID evidenceId) {}
  public record Explanation(Decision decision,List<Trace> sources) {}

  public Explanation explain(AuthorizationPolicyState state,UUID identity,String permission,UUID scope) {
    var decision=evaluate(state,identity,permission,scope);
    if(decision.reason()!=Reason.ALLOWED && decision.reason()!=Reason.EXPLICIT_DENY && decision.reason()!=Reason.NO_GRANT)
      return new Explanation(decision,List.of());
    var tree=new ScopeHierarchy(state.scopes()); var roles=effectiveRoles(state,identity,scope,tree);
    var sources=new ArrayList<Trace>();
    state.denies().stream().filter(d -> d.active() && d.permission().equals(permission) && tree.covers(d.scopeId(),scope)
        && (identity.equals(d.identityId()) || d.roleId()!=null && roles.contains(d.roleId())))
        .sorted(Comparator.comparing(d -> d.id().toString())).forEach(d ->
          sources.add(new Trace("DENY",!scope.equals(d.scopeId()),d.roleId(),d.scopeId(),d.id())));
    state.assignments().stream().filter(a -> a.active() && a.identityId().equals(identity) && roles.contains(a.roleId())
        && tree.active(a.scopeId()) && tree.covers(a.scopeId(),scope)
        && state.roles().get(a.roleId()).availableAt(a.scopeId(),tree,state.tenantScopes())
        && state.rolePermissions().getOrDefault(a.roleId(),Set.of()).contains(permission))
        .sorted(Comparator.comparing(a -> a.id().toString())).forEach(a ->
          sources.add(new Trace("ALLOW",!scope.equals(a.scopeId()),a.roleId(),a.scopeId(),a.id())));
    return new Explanation(decision,List.copyOf(sources));
  }

  public Decision evaluate(AuthorizationPolicyState state, UUID identity, String permission, UUID scope) {
    try { return evaluate(state,identity,permission,scope,new ScopeHierarchy(state.scopes())); }
    catch (IllegalArgumentException corruptHierarchy) { return new Decision(false,Reason.UNAVAILABLE_RESOURCE); }
  }

  public Decision evaluate(AuthorizationPolicyState state, UUID identity, String permission, UUID scope,ScopeHierarchy hierarchy) {
    if (identity==null || permission==null || scope==null) return new Decision(false,Reason.UNAVAILABLE_RESOURCE);
    if (!state.activeIdentities().contains(identity)) return new Decision(false,Reason.INACTIVE_IDENTITY);
    if (!state.deprecatedPermissions().containsKey(permission)) return new Decision(false,Reason.UNKNOWN_PERMISSION);
    try {
      boolean administration=permission.startsWith("AUTHORIZATION_") || permission.equals("TENANT_MANAGE") || permission.equals("TENANT_READ");
      if (!state.resourceAvailable(scope,hierarchy,administration)) return new Decision(false,Reason.UNAVAILABLE_RESOURCE);
      var roles=effectiveRoles(state,identity,scope,hierarchy);
      if (denied(state,identity,permission,scope,roles,hierarchy)) return new Decision(false,Reason.EXPLICIT_DENY);
      return roles.stream().anyMatch(id -> state.rolePermissions().getOrDefault(id,Set.of()).contains(permission))
          ? new Decision(true,Reason.ALLOWED) : new Decision(false,Reason.NO_GRANT);
    } catch (IllegalArgumentException corruptOrUnknownScope) {
      return new Decision(false,Reason.UNAVAILABLE_RESOURCE);
    }
  }

  public boolean mayDelegate(AuthorizationPolicyState state, UUID identity, String permission, UUID scope, boolean redelegating) {
    try { return mayDelegate(state,identity,permission,scope,redelegating,new ScopeHierarchy(state.scopes())); }
    catch(IllegalArgumentException corruptOrUnknownScope) { return false; }
  }

  public boolean mayDelegate(AuthorizationPolicyState state, UUID identity, String permission, UUID scope, boolean redelegating,ScopeHierarchy hierarchy) {
    if (identity==null || permission==null || scope==null) return false;
    if (!state.activeIdentities().contains(identity) || !Boolean.FALSE.equals(state.deprecatedPermissions().get(permission))) return false;
    try {
      // A live ancestor authority may configure an inactive descendant without activating access there.
      // An authority whose own administrative ancestry is inactive cannot be exercised.
      hierarchy.ancestors(scope);
      if (denied(state,identity,permission,scope,effectiveRoles(state,identity,scope,hierarchy),hierarchy)) return false;
      return state.delegations().stream().anyMatch(d -> d.active() && d.identityId().equals(identity)
          && d.permission().equals(permission) && hierarchy.active(d.scopeId()) && hierarchy.covers(d.scopeId(),scope)
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
