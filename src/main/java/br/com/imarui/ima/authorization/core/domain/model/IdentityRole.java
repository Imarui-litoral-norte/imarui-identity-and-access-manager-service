package br.com.imarui.ima.authorization.core.domain.model;

import java.time.Instant;
import java.util.*;

/** Explicit PLATFORM grant for the bootstrap only; not the final contextual RBAC model. */
public record IdentityRole(UUID identityId, Long roleId, Instant createdAt) {
  public IdentityRole {
    Objects.requireNonNull(identityId);
    if (roleId == null || roleId <= 0) throw new IllegalArgumentException("Invalid role.");
    Objects.requireNonNull(createdAt);
  }
}
