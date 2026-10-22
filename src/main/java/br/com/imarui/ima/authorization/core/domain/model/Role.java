package br.com.imarui.ima.authorization.core.domain.model;

import java.time.Instant;
import java.util.*;
import br.com.imarui.ima.authorization.core.domain.policy.ScopeHierarchy;

/** Ownership governs availability and administration; only assignments govern grants. */
public record Role(Long id, String name, Ownership ownership, UUID ownerScopeId, UUID ownerTenantId,
    boolean active, Instant createdAt, Instant updatedAt, long version) {
  public enum Ownership { GLOBAL, SCOPE, TENANT }
  public Role {
    if (name == null || name.isBlank() || name.length() > 100)
      throw new IllegalArgumentException("Invalid role name.");
    // Reconstitution preserves the persisted label exactly. Normalizing legacy labels here
    // could turn an ordinary " ROOT " row into the protected ROOT security identity.
    Objects.requireNonNull(ownership);
    Objects.requireNonNull(createdAt);
    Objects.requireNonNull(updatedAt);
    if ((id != null && id <= 0) || version < 0 || updatedAt.isBefore(createdAt))
      throw new IllegalArgumentException("Invalid Role state.");
    if ((ownership == Ownership.GLOBAL && (ownerScopeId != null || ownerTenantId != null))
        || (ownership == Ownership.SCOPE && (ownerScopeId == null || ownerTenantId != null))
        || (ownership == Ownership.TENANT && (ownerScopeId == null || ownerTenantId == null)))
      throw new IllegalArgumentException("Invalid Role ownership.");
    if (name.equalsIgnoreCase("ROOT") && (ownership != Ownership.GLOBAL || !name.equals("ROOT")))
      throw new IllegalArgumentException("ROOT is reserved to the central system.");
  }

  public boolean availableAt(UUID scope, ScopeHierarchy hierarchy, Map<UUID,UUID> tenantScopes) {
    return switch (ownership) {
      case GLOBAL -> true;
      case SCOPE -> hierarchy.covers(ownerScopeId, scope);
      case TENANT -> ownerScopeId.equals(scope) && ownerScopeId.equals(tenantScopes.get(ownerTenantId));
    };
  }
}
