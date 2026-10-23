package br.com.imarui.ima.authorization.core.domain.model;

import java.time.Instant;
import java.util.*;

/** Exactly one recipient. There are no priority or override fields. */
public record DenyRule(UUID id, String permission, UUID scopeId, UUID identityId, Long roleId,
    boolean active, String reason, Instant createdAt, Instant updatedAt, long version) {
  public DenyRule {
    Objects.requireNonNull(id); Objects.requireNonNull(scopeId); Objects.requireNonNull(createdAt);
    Objects.requireNonNull(updatedAt);
    if (permission == null || permission.isBlank() || permission.length()>100
        || (identityId == null) == (roleId == null) || (roleId != null && roleId <= 0)
        || reason == null || reason.isBlank() || reason.length()>500 || version<0 || updatedAt.isBefore(createdAt))
      throw new IllegalArgumentException("Invalid DENY rule.");
    reason=reason.trim();
  }
  public boolean sameTarget(String permission,UUID scope,UUID identity,Long role) {
    return this.permission.equals(permission) && scopeId.equals(scope)
        && Objects.equals(identityId,identity) && Objects.equals(roleId,role);
  }
}
