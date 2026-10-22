package br.com.imarui.ima.authorization.api.http;

import static br.com.imarui.ima.authorization.api.http.AuthorizationResponses.*;
import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.application.usecase.*;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.domain.policy.EffectiveAuthorizationEvaluator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/authorization/admin")
@RequiredArgsConstructor
@SecurityRequirement(name="bearerAuth")
@Tag(name="Authorization",description="Owner-aware administrative mutations and protected policy diagnostics")
public class AuthorizationAdminController {
  public record Change(@NotNull @Min(0) Long version,@NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  public record ScopeCreate(@NotBlank @Size(max=80) String code,@NotBlank @Size(max=150) String name,UUID parentId,
      @NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  public record ScopeRevision(@NotBlank @Size(max=150) String name,@NotNull AuthorizationScope.Status status,UUID parentId,
      @NotNull @Min(0) Long version,@NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  public record Reparent(UUID parentId,@NotNull @Min(0) Long version,@NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  public record RoleCreate(@NotBlank @Size(max=100) String name,@NotNull Role.Ownership ownership,UUID ownerScopeId,UUID ownerTenantId,
      @NotNull @Size(max=1000) Set<@NotBlank @Size(max=100) String> permissions,@NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  public record RoleRevision(@NotBlank @Size(max=100) String name,@NotNull Role.Status status,
      @NotNull @Size(max=1000) Set<@NotBlank @Size(max=100) String> permissions,@NotNull @Min(0) Long version,
      @NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  public record Lifecycle(@NotNull Role.Status status,@NotNull @Min(0) Long version,@NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  public record Permissions(@NotNull @Size(max=1000) Set<@NotBlank @Size(max=100) String> permissions,@NotNull @Min(0) Long version,
      @NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  public record Assign(@NotNull UUID identityId,@NotNull @Min(1) Long roleId,@NotNull UUID scopeId,
      @NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  public record Block(@NotBlank @Size(max=100) String permission,@NotNull UUID scopeId,UUID identityId,@Min(1) Long roleId,
      @NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  public record Delegate(@NotNull UUID identityId,@NotBlank @Size(max=100) String permission,@NotNull UUID scopeId,boolean redelegable,
      @NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  public record Owner(@NotBlank @Pattern(regexp="[a-z][a-z0-9-]{1,49}") String serviceCode,
      @NotBlank @Pattern(regexp="[a-z][a-z0-9-]{1,49}") String namespace,@NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  public record InitialAuthority(@NotBlank @Size(max=100) String permission,@NotNull UUID rootScopeId,
      @NotBlank @Size(max=500) String reason,@NotNull UUID correlationId) {}
  private final ScopeAdministrationUseCases scopes;
  private final RoleAdministrationUseCases roles;
  private final DenyDelegationUseCases rules;
  private final CatalogOwnerAdministrationUseCases owners;
  private final VersionedAuthorizationAdministration versioned;
  private final AuthorizationQueries queries;

  @PostMapping("/scopes") @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary="Create an administrative Scope without creating a Tenant")
  public Scope createScope(@Valid @RequestBody ScopeCreate r) { return Scope.from(scopes.create(r.code(),r.name(),r.parentId(),reason(r.reason(),r.correlationId()))); }
  @GetMapping("/scopes")
  public AuthorizationQueries.Page<Scope> scopes(@RequestParam UUID scopeId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
    return map(queries.scopes(scopeId,page,size),Scope::from);
  }
  @GetMapping("/scopes/{id}") public Scope scope(@PathVariable UUID id) { return Scope.from(queries.scope(id)); }
  @GetMapping("/scopes/{id}/ancestors")
  public AuthorizationQueries.Page<Scope> ancestors(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
    return map(queries.ancestors(id,page,size),Scope::from);
  }
  @PatchMapping("/scopes/{id}")
  public Scope reviseScope(@PathVariable UUID id,@Valid @RequestBody ScopeRevision r) {
    return Scope.from(versioned.reviseScope(id,r.name(),r.status(),r.parentId(),r.version(),reason(r.reason(),r.correlationId())));
  }
  @PostMapping("/scopes/{id}/reparent")
  public Scope reparent(@PathVariable UUID id,@Valid @RequestBody Reparent r) {
    return Scope.from(versioned.reparent(id,r.parentId(),r.version(),reason(r.reason(),r.correlationId())));
  }
  @DeleteMapping("/scopes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteScope(@PathVariable UUID id,@Valid @RequestBody Change r) { versioned.deleteScope(id,r.version(),reason(r.reason(),r.correlationId())); }

  @PostMapping("/roles") @ResponseStatus(HttpStatus.CREATED)
  public RoleResponse createRole(@Valid @RequestBody RoleCreate r) {
    return RoleResponse.from(roles.create(r.name(),r.ownership(),r.ownerScopeId(),r.ownerTenantId(),r.permissions(),reason(r.reason(),r.correlationId())));
  }
  @GetMapping("/roles")
  public AuthorizationQueries.Page<AuthorizationQueries.RoleView> roles(@RequestParam UUID scopeId,@RequestParam(required=false) Role.Ownership ownership,
      @RequestParam(required=false) Role.Status status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
    return queries.roles(scopeId,ownership,status,false,page,size);
  }
  @GetMapping("/roles/available")
  @Operation(summary="List available shared and owned Roles without granting assignments or ownership")
  public AuthorizationQueries.Page<AuthorizationQueries.RoleView> available(@RequestParam UUID scopeId,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return queries.roles(scopeId,null,null,true,page,size); }
  @GetMapping("/tenants/{tenantId}/roles/available")
  public AuthorizationQueries.Page<AuthorizationQueries.RoleView> tenantRoles(@PathVariable UUID tenantId,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return queries.tenantRoles(tenantId,page,size); }
  @GetMapping("/roles/{id}") public AuthorizationQueries.RoleView role(@PathVariable long id) { return queries.role(id); }
  @PatchMapping("/roles/{id}")
  public RoleResponse reviseRole(@PathVariable long id,@Valid @RequestBody RoleRevision r) {
    return RoleResponse.from(versioned.reviseRole(id,r.name(),r.status(),r.permissions(),r.version(),reason(r.reason(),r.correlationId())));
  }
  @PostMapping("/roles/{id}/lifecycle")
  public RoleResponse lifecycle(@PathVariable long id,@Valid @RequestBody Lifecycle r) {
    return RoleResponse.from(versioned.lifecycle(id,r.status(),r.version(),reason(r.reason(),r.correlationId())));
  }
  @DeleteMapping("/roles/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteRole(@PathVariable long id,@Valid @RequestBody Change r) { versioned.deleteRole(id,r.version(),reason(r.reason(),r.correlationId())); }
  @GetMapping("/roles/{id}/permissions") public Set<String> permissions(@PathVariable long id) { return queries.rolePermissions(id); }
  @PutMapping("/roles/{id}/permissions")
  public RoleResponse composition(@PathVariable long id,@Valid @RequestBody Permissions r) {
    return RoleResponse.from(versioned.permissions(id,r.permissions(),r.version(),reason(r.reason(),r.correlationId())));
  }

  @PostMapping("/assignments")
  public Assignment assign(@Valid @RequestBody Assign r) { return Assignment.from(roles.assign(r.identityId(),r.roleId(),r.scopeId(),reason(r.reason(),r.correlationId()))); }
  @GetMapping("/assignments")
  public AuthorizationQueries.Page<Assignment> assignments(@RequestParam UUID scopeId,@RequestParam(required=false) UUID identityId,
      @RequestParam(defaultValue="false") boolean inherited,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
    return map(queries.assignments(scopeId,identityId,inherited,page,size),Assignment::from);
  }
  @PostMapping("/assignments/{id}/revoke") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokeAssignment(@PathVariable UUID id,@Valid @RequestBody Change r) { versioned.revokeAssignment(id,r.version(),reason(r.reason(),r.correlationId())); }
  @PostMapping("/denies")
  public Deny deny(@Valid @RequestBody Block r) { return Deny.from(rules.deny(r.permission(),r.scopeId(),r.identityId(),r.roleId(),reason(r.reason(),r.correlationId()))); }
  @GetMapping("/denies")
  public AuthorizationQueries.Page<Deny> denies(@RequestParam UUID scopeId,@RequestParam(required=false) Boolean active,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return map(queries.denies(scopeId,active,page,size),Deny::from); }
  @PostMapping("/denies/{id}/revoke") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokeDeny(@PathVariable UUID id,@Valid @RequestBody Change r) { versioned.revokeDeny(id,r.version(),reason(r.reason(),r.correlationId())); }
  @PostMapping("/delegations")
  public Delegation delegate(@Valid @RequestBody Delegate r) { return Delegation.from(rules.delegate(r.identityId(),r.permission(),r.scopeId(),r.redelegable(),reason(r.reason(),r.correlationId()))); }
  @GetMapping("/delegations")
  public AuthorizationQueries.Page<Delegation> delegations(@RequestParam UUID scopeId,@RequestParam(required=false) UUID identityId,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return map(queries.delegations(scopeId,identityId,page,size),Delegation::from); }
  @PostMapping("/delegations/{id}/revoke") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokeDelegation(@PathVariable UUID id,@Valid @RequestBody Change r) { versioned.revokeDelegation(id,r.version(),reason(r.reason(),r.correlationId())); }

  @PostMapping("/catalog/owners") public Map<String,Boolean> owner(@Valid @RequestBody Owner r) {
    return Map.of("changed",owners.enroll(r.serviceCode(),r.namespace(),reason(r.reason(),r.correlationId())));
  }
  @PostMapping("/catalog/initial-authority") public Delegation initialAuthority(@Valid @RequestBody InitialAuthority r) {
    return Delegation.from(rules.bootstrapCatalogAuthority(r.permission(),r.rootScopeId(),reason(r.reason(),r.correlationId())));
  }
  @GetMapping("/catalog/permissions")
  public AuthorizationQueries.Page<AuthorizationQueries.PermissionView> catalog(@RequestParam UUID scopeId,
      @RequestParam(required=false) String owner,@RequestParam(required=false) String prefix,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return queries.permissions(scopeId,owner,prefix,page,size); }
  @GetMapping("/effective-permissions")
  public AuthorizationQueries.Page<AuthorizationQueries.EffectivePermission> effective(@RequestParam UUID identityId,@RequestParam UUID scopeId,
      @RequestParam(required=false) String prefix,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return queries.effective(identityId,scopeId,prefix,page,size); }
  @GetMapping("/decisions/explain")
  @Operation(summary="Explain direct/inherited ALLOW and DENY; requires scoped audit-read authority")
  public EffectiveAuthorizationEvaluator.Explanation explain(@RequestParam UUID identityId,@RequestParam String permission,@RequestParam UUID scopeId) {
    return queries.explain(identityId,permission,scopeId);
  }
  private PolicyChangeReason reason(String reason,UUID correlation) { return new PolicyChangeReason(reason,correlation); }
}
