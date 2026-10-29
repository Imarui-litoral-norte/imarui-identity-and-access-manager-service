package br.com.imarui.ima.authorization.infra.security;

import br.com.imarui.ima.authentication.core.port.AuthenticationOperationAccess;
import br.com.imarui.ima.identity.core.application.port.IdentityOperationAccess;
import br.com.imarui.ima.authorization.core.application.port.AuthorizationActor;
import br.com.imarui.ima.authorization.core.application.usecase.EffectiveAuthorizationUseCases;
import br.com.imarui.ima.authorization.core.domain.model.AuthorizationScope;
import br.com.imarui.ima.authorization.core.repository.ScopeRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Cross-module ports reuse the central evaluator and the stateful human-session verifier. */
@Component
@RequiredArgsConstructor
public class ModuleOperationAccessAdapter implements IdentityOperationAccess, AuthenticationOperationAccess, br.com.imarui.ima.identity.core.application.port.TenantDirectoryAccess {
  private final AuthorizationActor actors;
  private final EffectiveAuthorizationUseCases authorizations;
  private final ScopeRepository scopes;

  public java.util.Set<UUID> readableTenantIds() {
    return authorizations.readableTenants(actors.requireIdentityId());
  }

  public void requirePlatform(String permission) {
    require(actors.requireIdentityId(), permission, AuthorizationScope.PLATFORM_ID);
  }

  public void requireTenant(String permission, UUID tenant) {
    var actor = actors.requireIdentityId();
    var scope = scopes.tenantScope(tenant).orElseThrow(() -> new AccessDeniedException("Missing Tenant scope."));
    require(actor, permission, scope);
  }

  public void requireSelfOrPlatform(String permission, UUID identity) {
    var actor = actors.requireIdentityId();
    if (!actor.equals(identity)) require(actor, permission, AuthorizationScope.PLATFORM_ID);
  }

  public void requireIdentity(UUID identity) {
    if (!actors.requireIdentityId().equals(identity))
      throw new AccessDeniedException("The current actor does not own this operation.");
  }

  private void require(UUID actor, String permission, UUID scope) {
    if (!authorizations.evaluate(actor, permission, scope).allowed())
      throw new AccessDeniedException("Operation denied by the current policy.");
  }
}
