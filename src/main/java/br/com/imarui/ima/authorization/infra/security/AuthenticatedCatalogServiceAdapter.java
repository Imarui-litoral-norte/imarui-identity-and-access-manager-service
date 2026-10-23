package br.com.imarui.ima.authorization.infra.security;

import br.com.imarui.ima.authorization.core.application.port.CatalogServiceAuthentication;
import br.com.imarui.ima.authentication.api.module.TechnicalServiceAuthentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@lombok.RequiredArgsConstructor
public class AuthenticatedCatalogServiceAdapter implements CatalogServiceAuthentication {
  private final TechnicalServiceAuthentication authentication;
  public String requireServiceCode() {
    var auth=SecurityContextHolder.getContext().getAuthentication();
    if(auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof TechnicalServiceAuthentication.Caller caller)
        || !caller.operations().contains(TechnicalServiceAuthentication.Operation.CATALOG_SYNC))
      throw new SecurityException("Authenticated catalog service required.");
    var current=authentication.current(caller);
    if(!current.operations().contains(TechnicalServiceAuthentication.Operation.CATALOG_SYNC))
      throw new SecurityException("Current catalog authority required.");
    return current.serviceCode();
  }
}
