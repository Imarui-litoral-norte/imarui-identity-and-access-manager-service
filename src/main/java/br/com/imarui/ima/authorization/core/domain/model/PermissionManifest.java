package br.com.imarui.ima.authorization.core.domain.model;

import br.com.imarui.ima.authorization.api.catalog.*;
import java.io.*;
import java.security.*;
import java.util.*;

/** Canonical service-owned catalog. Version reuse with a different digest is forbidden. */
public record PermissionManifest(String owner, String namespace, long version, List<Entry> permissions) {
  public record Entry(String code, String description, String module, boolean deprecated) {
    public Entry {
      if (code == null || !code.matches("[A-Za-z][A-Za-z0-9_.:-]{0,99}"))
        throw new IllegalArgumentException("Invalid Permission code.");
      if (description == null || description.isBlank() || description.length() > 255)
        throw new IllegalArgumentException("Invalid Permission description.");
      if (module == null || !module.matches("[A-Za-z][A-Za-z0-9_.-]{0,39}"))
        throw new IllegalArgumentException("Invalid Permission module.");
      description = description.trim();
    }
  }

  public PermissionManifest {
    if (owner == null || !owner.matches("[a-z][a-z0-9-]{1,49}") || namespace == null
        || !namespace.matches("[a-z][a-z0-9-]{1,49}") || version < 1)
      throw new IllegalArgumentException("Invalid catalog owner, namespace or version.");
    Objects.requireNonNull(permissions, "Permission entries required.");
    if (permissions.size() > 10000) throw new IllegalArgumentException("Catalog too large.");
    permissions = permissions.stream().sorted(Comparator.comparing(Entry::code)).toList();
    var codes = new HashSet<String>();
    for (var entry : permissions) {
      if (!codes.add(entry.code())) throw new IllegalArgumentException("Duplicate Permission code.");
      if (!entry.code().startsWith(namespace + ".")) {
        // Existing IIAM codes are stable business keys; no renaming or duplicate aliases.
        if (!owner.equals("iiam") || !namespace.equals("iiam"))
          throw new IllegalArgumentException("Permission belongs to another namespace.");
        try { PermissionCode.valueOf(entry.code()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown internal Permission."); }
      }
    }
  }

  public String digest() {
    try {
      var buffer = new ByteArrayOutputStream();
      try (var out = new DataOutputStream(buffer)) {
        out.writeUTF(owner); out.writeUTF(namespace); out.writeLong(version);
        for (var entry : permissions) {
          out.writeUTF(entry.code()); out.writeUTF(entry.description());
          out.writeUTF(entry.module()); out.writeBoolean(entry.deprecated());
        }
      }
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(buffer.toByteArray()));
    } catch (IOException | NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }

  public static PermissionManifest internal() {
    return new PermissionManifest("iiam", "iiam", 2, Arrays.stream(PermissionDefinition.values())
        .map(d -> new Entry(d.code().name(), d.description(), d.module().name(), false)).toList());
  }
}
