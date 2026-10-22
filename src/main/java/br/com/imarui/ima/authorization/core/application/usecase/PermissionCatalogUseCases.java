package br.com.imarui.ima.authorization.core.application.usecase;

import br.com.imarui.ima.authorization.core.application.port.CatalogServiceAuthentication;
import br.com.imarui.ima.authorization.core.domain.model.PermissionManifest;
import br.com.imarui.ima.authorization.core.repository.PermissionCatalogRepository;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PermissionCatalogUseCases {
  public static class CatalogConflict extends IllegalArgumentException {
    public CatalogConflict(String message) { super(message); }
  }
  private final PermissionCatalogRepository catalog;
  private final Optional<CatalogServiceAuthentication> technicalIdentity;
  private final Clock clock;

  /** Fixed definitions only; no external data and no grant side effects. */
  @Transactional
  public boolean synchronizeInternal() {
    catalog.lock();
    return synchronize(PermissionManifest.internal());
  }

  /** No HTTP adapter is published in Round 1. Missing technical authentication rejects the call. */
  @Transactional
  public boolean synchronizeExternal(PermissionManifest manifest) {
    var identity = technicalIdentity.orElseThrow(() -> new SecurityException("Technical service authentication required."));
    catalog.lock();
    String caller = identity.requireServiceCode();
    if (caller == null || !caller.equals(manifest.owner()) || caller.equals("iiam"))
      throw new SecurityException("Catalog owner does not match the authenticated service.");
    return synchronize(manifest);
  }

  private boolean synchronize(PermissionManifest manifest) {
    var owner = catalog.owner(manifest.owner()).orElseThrow(() -> new SecurityException("Catalog owner is not enrolled."));
    if (!owner.namespace().equals(manifest.namespace())) throw new SecurityException("Namespace is immutable.");
    if (manifest.version() < owner.version()) throw new CatalogConflict("Catalog rollback rejected.");
    if (manifest.version() == owner.version()) {
      if (!manifest.digest().equals(owner.digest())) throw new CatalogConflict("Catalog version conflict.");
      return false;
    }
    catalog.synchronize(manifest, clock.instant());
    return true;
  }
}
