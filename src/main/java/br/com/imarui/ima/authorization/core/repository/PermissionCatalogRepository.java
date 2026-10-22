package br.com.imarui.ima.authorization.core.repository;

import br.com.imarui.ima.authorization.core.domain.model.PermissionManifest;
import java.time.Instant;
import java.util.Optional;

public interface PermissionCatalogRepository {
  record Owner(String code, String namespace, long version, String digest) {}
  void lock();
  Optional<Owner> owner(String code);
  void synchronize(PermissionManifest manifest, Instant now);
}
