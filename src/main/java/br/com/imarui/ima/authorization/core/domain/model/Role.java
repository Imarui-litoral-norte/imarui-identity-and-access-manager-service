package br.com.imarui.ima.authorization.core.domain.model;

import java.time.Instant;

/** V1 foundation. Administration and contextual assignments are deliberately not exposed. */
public record Role(Long id, String name, boolean active, Instant createdAt) {
  public Role {
    if (name == null || name.isBlank() || name.length() > 100)
      throw new IllegalArgumentException("Invalid role name.");
  }
}
