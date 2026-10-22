package br.com.imarui.ima.authorization.infra.persistence;

import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.domain.policy.AuthorizationPolicyState;
import br.com.imarui.ima.authorization.core.repository.*;
import br.com.imarui.ima.identity.api.module.IdentityAuthorizationApi;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@RequiredArgsConstructor
public class JdbcAuthorizationPolicyRepository implements AuthorizationPolicyRepository {
  private final JdbcTemplate jdbc;
  private final ScopeRepository scopes;
  private final IdentityAuthorizationApi identities;

  @Transactional(propagation=Propagation.MANDATORY)
  public AuthorizationPolicyState current() {
    var roles=new HashMap<Long,Role>();
    jdbc.query("select * from authorization_roles", (org.springframework.jdbc.core.RowCallbackHandler) r -> {
      var id=r.getLong("id");
      roles.put(id,new Role(id,r.getString("name"),Role.Ownership.valueOf(r.getString("ownership")),
          r.getObject("owner_scope_id",UUID.class),r.getObject("owner_tenant_id",UUID.class),r.getBoolean("active"),
          r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),r.getLong("version")));
    });
    var rolePermissions=new HashMap<Long,Set<String>>();
    jdbc.query("select rp.role_id,p.code from authorization_role_permissions rp join authorization_permissions p on p.id=rp.permission_id",
        (org.springframework.jdbc.core.RowCallbackHandler) r -> rolePermissions.computeIfAbsent(r.getLong("role_id"), k -> new HashSet<>()).add(r.getString("code")));
    var assignments=jdbc.query("select * from authorization_identity_roles",(r,n) -> new RoleAssignment(
        r.getObject("id",UUID.class),r.getObject("identity_id",UUID.class),r.getLong("role_id"),
        r.getObject("authorization_scope_id",UUID.class),r.getBoolean("active"),r.getTimestamp("created_at").toInstant(),
        r.getTimestamp("updated_at").toInstant(),r.getLong("version")));
    var denies=jdbc.query("select * from authorization_denies",(r,n) -> new DenyRule(r.getObject("id",UUID.class),
        r.getString("permission_code"),r.getObject("scope_id",UUID.class),r.getObject("identity_id",UUID.class),
        r.getObject("role_id",Long.class),r.getBoolean("active"),r.getString("reason"),
        r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),r.getLong("version")));
    var delegations=jdbc.query("select * from authorization_permission_delegations",(r,n) -> new PermissionDelegation(
        r.getObject("id",UUID.class),r.getObject("identity_id",UUID.class),r.getString("permission_code"),
        r.getObject("scope_id",UUID.class),r.getBoolean("redelegable"),r.getBoolean("active"),r.getString("reason"),
        r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),r.getLong("version")));
    var permissions=new HashMap<String,Boolean>();
    jdbc.query("select code,deprecated from authorization_permissions",(org.springframework.jdbc.core.RowCallbackHandler) r -> permissions.put(r.getString("code"),r.getBoolean("deprecated")));
    var bindings=new HashMap<UUID,UUID>();
    jdbc.query("select tenant_id,scope_id from authorization_tenant_scopes",(org.springframework.jdbc.core.RowCallbackHandler) r -> bindings.put(r.getObject("tenant_id",UUID.class),r.getObject("scope_id",UUID.class)));
    return new AuthorizationPolicyState(scopes.all(),roles,rolePermissions,assignments,denies,delegations,permissions,
        bindings,identities.activeIdentityIds(),identities.activeTenantIds());
  }
}
