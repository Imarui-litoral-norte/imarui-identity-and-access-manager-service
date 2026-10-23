package br.com.imarui.ima.authorization.core.application.usecase;
import br.com.imarui.ima.authorization.core.application.port.AuthorizationActor;
import br.com.imarui.ima.authorization.core.application.service.AuthorizationMutationGuard;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=5)
public class CatalogInspectionQueries {
  private final CatalogInspectionRepository catalogs;
  private final AuthorizationActor actors;
  private final AuthorizationMutationGuard guard;
  private final AuthorizationPolicyRepository policies;
  private void authorized() { guard.require(policies.current(),actors.requireIdentityId(),"AUTHORIZATION_AUDIT_READ",AuthorizationScope.PLATFORM_ID); }
  public AuthorizationQueries.Page<CatalogInspectionRepository.Owner> owners(int page,int size) {
    authorized(); AuthorizationQueries.page(List.of(),page,size);
    return new AuthorizationQueries.Page<>(catalogs.owners(page*size,size),catalogs.ownerCount(),page,size);
  }
  public CatalogInspectionRepository.Owner owner(String code) {
    authorized(); return catalogs.owner(code).orElseThrow(() -> new NoSuchElementException("Catalog owner not found."));
  }
  public AuthorizationQueries.Page<CatalogInspectionRepository.Revision> revisions(String code,int page,int size) {
    owner(code); AuthorizationQueries.page(List.of(),page,size);
    return new AuthorizationQueries.Page<>(catalogs.revisions(code,page*size,size),catalogs.revisionCount(code),page,size);
  }
  public PermissionManifest manifest(String code,long version) {
    authorized(); return catalogs.manifest(code,version).orElseThrow(() -> new NoSuchElementException("Manifest revision not found."));
  }
}
