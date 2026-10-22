package br.com.imarui.ima.authorization.core.domain.policy;

import br.com.imarui.ima.authorization.core.domain.model.*;
import java.util.*;

/** One consistent policy revision from persistence, with no session or JWT authority cache. */
public record AuthorizationPolicyState(List<AuthorizationScope> scopes, Map<Long,Role> roles,
    Map<Long,Set<String>> rolePermissions, List<RoleAssignment> assignments, List<DenyRule> denies,
    List<PermissionDelegation> delegations, Map<String,Boolean> deprecatedPermissions,
    Map<UUID,UUID> tenantScopes, Set<UUID> activeIdentities, Set<UUID> activeTenants) {
  public AuthorizationPolicyState {
    scopes=List.copyOf(scopes); roles=Map.copyOf(roles);
    var permissions=new HashMap<Long,Set<String>>();
    rolePermissions.forEach((id,codes) -> permissions.put(id,Set.copyOf(codes)));
    rolePermissions=Map.copyOf(permissions); assignments=List.copyOf(assignments); denies=List.copyOf(denies);
    delegations=List.copyOf(delegations); deprecatedPermissions=Map.copyOf(deprecatedPermissions);
    tenantScopes=Map.copyOf(tenantScopes); activeIdentities=Set.copyOf(activeIdentities); activeTenants=Set.copyOf(activeTenants);
  }

  public boolean resourceAvailable(UUID scope, ScopeHierarchy hierarchy) {
    if (!hierarchy.active(scope)) return false;
    return tenantScopes.entrySet().stream().noneMatch(binding ->
        hierarchy.covers(binding.getValue(),scope) && !activeTenants.contains(binding.getKey()));
  }
}
