package br.com.imarui.ima.authorization.core.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An administrative resource boundary. It is neither a Tenant nor a geographic mandate. */
public record AuthorizationScope(
    UUID id, String code, String name, Status status, UUID parentId,
    Instant createdAt, Instant updatedAt, long version) {
  public static final UUID PLATFORM_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  public enum Status { ACTIVE, DISABLED }

  public AuthorizationScope {
    Objects.requireNonNull(id, "Scope id required.");
    if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,79}"))
      throw new IllegalArgumentException("Scope code must be canonical and contain 1 to 80 characters.");
    if (name == null || name.isBlank() || name.length() > 150)
      throw new IllegalArgumentException("Scope name must contain 1 to 150 characters.");
    name = name.trim();
    Objects.requireNonNull(status, "Scope status required.");
    Objects.requireNonNull(createdAt, "Scope creation time required.");
    Objects.requireNonNull(updatedAt, "Scope update time required.");
    if (id.equals(parentId) || updatedAt.isBefore(createdAt) || version < 0)
      throw new IllegalArgumentException("Invalid Scope state.");
  }

  public static AuthorizationScope create(String code, String name, UUID parentId, Instant now) {
    return new AuthorizationScope(UUID.randomUUID(), code, name, Status.ACTIVE, parentId, now, now, 0);
  }

  public AuthorizationScope revise(String name, Status status, UUID parentId, Instant now) {
    if (PLATFORM_ID.equals(id) && (status != Status.ACTIVE || parentId != null))
      throw new IllegalArgumentException("The administrative recovery boundary is protected.");
    return new AuthorizationScope(id, code, name, status, parentId, createdAt, now, version + 1);
  }
}
