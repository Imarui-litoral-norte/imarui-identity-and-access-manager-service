package br.com.imarui.ima.authorization.core.application.usecase;
import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.application.service.AuthorizationMutationGuard;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.repository.*;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permission-namespace governance only; no operational service registry or Tenant enablement. */
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogOwnerAdministrationUseCases {
  private final AuthorizationMutationGuard guard;
  private final PermissionCatalogRepository catalogs;
  private final AuthorizationMutationRepository mutations;
  private final Clock clock;
  public boolean enroll(String owner,String namespace,PolicyChangeReason reason) {
    var context=guard.begin("AUTHORIZATION_CATALOG_MANAGE",AuthorizationScope.PLATFORM_ID,reason);
    new PermissionManifest(owner,namespace,1,List.of());
    var existing=catalogs.owner(owner);
    if (existing.isPresent()) {
      if (!existing.get().namespace().equals(namespace)) throw new IllegalArgumentException("Catalog namespace is immutable.");
      return false;
    }
    var now=clock.instant(); catalogs.enroll(owner,namespace,now);
    mutations.audit(context.actor(),"CATALOG_OWNER_ENROLLED","CATALOG_OWNER",owner,AuthorizationScope.PLATFORM_ID,
        reason,null,Map.of("owner",owner,"namespace",namespace),now);
    return true;
  }
}
