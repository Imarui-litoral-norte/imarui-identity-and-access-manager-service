package br.com.imarui.ima.authorization.core.domain.model;

import java.time.Instant;
import java.util.*;

/** Grant authority is independent of permission use; redelegation is explicit. */
public record PermissionDelegation(UUID id, UUID identityId, String permission, UUID scopeId,
    boolean redelegable, boolean active, String reason, Instant createdAt, Instant updatedAt, long version) {
  public PermissionDelegation {
    Objects.requireNonNull(id); Objects.requireNonNull(identityId); Objects.requireNonNull(scopeId);
    Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
    if (permission == null || permission.isBlank() || permission.length()>100 || reason == null
        || reason.isBlank() || reason.length()>500 || version<0 || updatedAt.isBefore(createdAt))
      throw new IllegalArgumentException("Invalid Permission delegation.");
    reason=reason.trim();
  }
}
