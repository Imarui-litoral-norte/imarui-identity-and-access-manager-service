// Adapted from Jeep Club master 9ad04a71e50917e0a53c1c4855ea6770730bd8b9; see
// docs/authentication/reuse-matrix.md
package br.com.imarui.ima.authorization.core.domain.model;

import br.com.imarui.ima.authorization.api.catalog.ModuleCode;
import br.com.imarui.ima.authorization.api.catalog.PermissionCode;
import br.com.imarui.ima.authorization.api.catalog.PermissionDefinition;
import br.com.imarui.ima.authorization.core.domain.exception.permission.PermissionCodeMismatchException;
import br.com.imarui.ima.authorization.core.domain.exception.permission.PermissionDescriptionCannotBeBlankException;
import br.com.imarui.ima.authorization.core.domain.exception.permission.PermissionDescriptionTooLongException;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Permission {

  private Long id;
  private PermissionCode code;
  private String description;
  private ModuleCode module;
  private Instant createdAt;
  private Instant updatedAt;

  private Permission(
      Long id,
      PermissionCode code,
      String description,
      ModuleCode module,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.code = Objects.requireNonNull(code, "Permission code cannot be null");
    this.description = validateDescription(description);
    this.module = Objects.requireNonNull(module, "Permission module cannot be null");
    this.createdAt = Objects.requireNonNull(createdAt, "Permission createdAt cannot be null");
    this.updatedAt = updatedAt;
  }

  public static Permission fromDefinition(PermissionDefinition definition, Instant now) {
    Objects.requireNonNull(definition, "Permission definition cannot be null");
    Objects.requireNonNull(now, "now cannot be null");

    return new Permission(
        null, definition.getCode(), definition.getDescription(), definition.getModule(), now, now);
  }

  public static Permission reconstitute(
      Long id,
      PermissionCode code,
      String description,
      ModuleCode module,
      Instant createdAt,
      Instant updatedAt) {
    Objects.requireNonNull(id, "Permission id cannot be null when reconstituting");

    return new Permission(id, code, description, module, createdAt, updatedAt);
  }

  public boolean synchronizeWith(PermissionDefinition definition, Instant synchronizedAt) {
    Objects.requireNonNull(definition, "Permission definition cannot be null");
    Objects.requireNonNull(synchronizedAt, "synchronizedAt cannot be null");

    if (this.code != definition.getCode()) {
      throw new PermissionCodeMismatchException(this.code, definition.getCode());
    }

    String synchronizedDescription = validateDescription(definition.getDescription());
    ModuleCode synchronizedModule =
        Objects.requireNonNull(definition.getModule(), "Permission module cannot be null");

    boolean changed = false;

    if (!Objects.equals(this.description, synchronizedDescription)) {
      this.description = synchronizedDescription;
      changed = true;
    }

    if (this.module != synchronizedModule) {
      this.module = synchronizedModule;
      changed = true;
    }

    if (changed) {
      this.updatedAt = synchronizedAt;
    }

    return changed;
  }

  private static String validateDescription(String description) {
    if (description == null || description.isBlank()) {
      throw new PermissionDescriptionCannotBeBlankException();
    }

    String normalizedDescription = description.trim();

    if (normalizedDescription.length() > 255) {
      throw new PermissionDescriptionTooLongException(255);
    }

    return normalizedDescription;
  }
}
