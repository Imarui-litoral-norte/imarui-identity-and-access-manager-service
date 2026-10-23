package br.com.imarui.ima.authorization.api.http;

import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.application.usecase.AuthorizationQueries.Page;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

public final class AuthorizationResponses {
  private AuthorizationResponses() {}
  public record Scope(UUID id,String code,String name,AuthorizationScope.Status status,UUID parentId,Instant createdAt,Instant updatedAt,long version) {
    public static Scope from(AuthorizationScope s) { return new Scope(s.id(),s.code(),s.name(),s.status(),s.parentId(),s.createdAt(),s.updatedAt(),s.version()); }
  }
  public record RoleResponse(Long id,String name,Role.Ownership ownership,UUID ownerScopeId,UUID ownerTenantId,Role.Status status,Instant createdAt,Instant updatedAt,long version) {
    public static RoleResponse from(Role r) { return new RoleResponse(r.id(),r.name(),r.ownership(),r.ownerScopeId(),r.ownerTenantId(),r.status(),r.createdAt(),r.updatedAt(),r.version()); }
  }
  public record Assignment(UUID id,UUID identityId,long roleId,UUID scopeId,boolean active,Instant createdAt,Instant updatedAt,long version) {
    public static Assignment from(RoleAssignment a) { return new Assignment(a.id(),a.identityId(),a.roleId(),a.scopeId(),a.active(),a.createdAt(),a.updatedAt(),a.version()); }
  }
  public record Deny(UUID id,String permission,UUID scopeId,UUID identityId,Long roleId,boolean active,String reason,Instant createdAt,Instant updatedAt,long version) {
    public static Deny from(DenyRule d) { return new Deny(d.id(),d.permission(),d.scopeId(),d.identityId(),d.roleId(),d.active(),d.reason(),d.createdAt(),d.updatedAt(),d.version()); }
  }
  public record Delegation(UUID id,UUID identityId,String permission,UUID scopeId,boolean redelegable,boolean active,String reason,Instant createdAt,Instant updatedAt,long version) {
    public static Delegation from(PermissionDelegation d) { return new Delegation(d.id(),d.identityId(),d.permission(),d.scopeId(),d.redelegable(),d.active(),d.reason(),d.createdAt(),d.updatedAt(),d.version()); }
  }
  public static <T,R> Page<R> map(Page<T> page,Function<T,R> mapper) { return new Page<>(page.items().stream().map(mapper).toList(),page.total(),page.page(),page.size()); }
}
