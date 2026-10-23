package br.com.imarui.ima.authorization.core.domain.model;

import java.time.Instant;
import java.util.*;

public record RoleAssignment(UUID id, UUID identityId, long roleId, UUID scopeId, boolean active,
    Instant createdAt, Instant updatedAt, long version) {
  public RoleAssignment {
    Objects.requireNonNull(id); Objects.requireNonNull(identityId); Objects.requireNonNull(scopeId);
    Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
    if (roleId <= 0 || version < 0 || updatedAt.isBefore(createdAt))
      throw new IllegalArgumentException("Invalid RoleAssignment state.");
  }
}
