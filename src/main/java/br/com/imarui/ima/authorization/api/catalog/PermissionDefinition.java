package br.com.imarui.ima.authorization.api.catalog;

import java.util.Arrays;

public record PermissionDefinition(PermissionCode code, String description, ModuleCode module) {
  public PermissionCode getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  public ModuleCode getModule() {
    return module;
  }

  public static PermissionDefinition[] values() {
    return Arrays.stream(PermissionCode.values())
        .map(
            c ->
                new PermissionDefinition(
                    c,
                    c.name().replace('_', ' '),
                    c.name().startsWith("AUTHENTICATION")
                        ? ModuleCode.AUTHENTICATION
                        : c.name().startsWith("AUTHORIZATION")
                            ? ModuleCode.AUTHORIZATION : ModuleCode.IDENTITY))
        .toArray(PermissionDefinition[]::new);
  }
}
