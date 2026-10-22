package br.com.imarui.ima.authorization.infra.security;
import br.com.imarui.ima.authorization.core.application.port.AuthorizationActor;
import br.com.imarui.ima.authorization.core.application.usecase.EffectiveAuthorizationUseCases;
import br.com.imarui.ima.authorization.core.domain.model.AuthorizationScope;
import br.com.imarui.ima.authorization.core.repository.ScopeRepository;
import br.com.imarui.ima.identity.api.module.IdentityAuthorizationApi;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolve the authoritative binding from the resource Tenant, never from a frontend scope parameter. */
@Component("tenantAuthorizationAccess")
@RequiredArgsConstructor
public class TenantAuthorizationAccess {
  private final AuthorizationActor actors;
  private final ScopeRepository scopes;
  private final IdentityAuthorizationApi identities;
  private final EffectiveAuthorizationUseCases authorizations;
  public boolean allowed(String permission,String tenantId) {
    UUID actor=actors.requireIdentityId();
    UUID tenant;
    try { tenant=UUID.fromString(tenantId); }
    catch (IllegalArgumentException invalid) {
      // Preserve the existing 400 contract for a centrally authorized caller; parsing remains in Identity.
      return authorizations.evaluate(actor,permission,AuthorizationScope.PLATFORM_ID).allowed();
    }
    var scope=scopes.tenantScope(tenant);
    if (scope.isEmpty()) {
      if (identities.tenantExists(tenant)) return false;
      // A global administrator may receive the existing 404 response for an absent resource.
      return authorizations.evaluate(actor,permission,AuthorizationScope.PLATFORM_ID).allowed();
    }
    return authorizations.evaluate(actor,permission,scope.get()).allowed();
  }
}
