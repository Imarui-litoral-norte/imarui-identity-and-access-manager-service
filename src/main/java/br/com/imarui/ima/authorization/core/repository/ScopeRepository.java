package br.com.imarui.ima.authorization.core.repository;

import br.com.imarui.ima.authorization.core.domain.model.AuthorizationScope;
import java.util.*;

public interface ScopeRepository {
  List<AuthorizationScope> all();
  Optional<AuthorizationScope> find(UUID id);
  List<AuthorizationScope> ancestors(UUID id);
  List<AuthorizationScope> descendants(UUID id);
  Optional<UUID> tenantScope(UUID tenantId);
  void save(AuthorizationScope scope);
}
