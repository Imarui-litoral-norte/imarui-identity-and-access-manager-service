package br.com.imarui.ima.authorization.infra.persistence;

import br.com.imarui.ima.authorization.api.catalog.*;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.repository.AuthorizationCatalog;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuthorizationCatalogAdapter implements AuthorizationCatalog {
  private final JdbcTemplate jdbc;

  public List<Permission> permissions() {
    return jdbc.query(
        "select * from authorization_permissions order by code",
        (r, n) ->
            Permission.reconstitute(
                r.getLong("id"),
                r.getString("code"),
                r.getString("description"),
                r.getString("module"),
                r.getString("service_code"),
                r.getString("namespace"),
                r.getLong("catalog_version"),
                r.getBoolean("deprecated"),
                r.getTimestamp("created_at").toInstant(),
                r.getTimestamp("updated_at").toInstant()));
  }

  public Optional<Role> role(long id) {
    return jdbc
        .query(
            "select * from authorization_roles where id=?",
            (r, n) ->
                new Role(
                    id,
                    r.getString("name"),
                    Role.Ownership.valueOf(r.getString("ownership")),
                    r.getObject("owner_scope_id", UUID.class),
                    r.getObject("owner_tenant_id", UUID.class),
                    Role.Status.valueOf(r.getString("status")),
                    r.getTimestamp("created_at").toInstant(),
                    r.getTimestamp("updated_at").toInstant(), r.getLong("version")),
            id)
        .stream()
        .findFirst();
  }

  public List<RolePermission> rolePermissions(long id) {
    return jdbc.query(
        "select * from authorization_role_permissions where role_id=?",
        (r, n) ->
            RolePermission.reconstitute(
                r.getLong("id"),
                r.getLong("role_id"),
                r.getLong("permission_id"),
                r.getTimestamp("created_at").toInstant()),
        id);
  }

  public List<RoleAssignment> assignments(UUID id) {
    return jdbc.query(
        "select * from authorization_identity_roles where identity_id=?",
        (r, n) ->
            new RoleAssignment(r.getObject("id",UUID.class),id,r.getLong("role_id"),
                r.getObject("authorization_scope_id",UUID.class),r.getBoolean("active"),
                r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),r.getLong("version")),
        id);
  }
}
