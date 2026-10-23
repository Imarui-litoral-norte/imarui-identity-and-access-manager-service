package br.com.imarui.ima.authorization.core.domain.policy;

import br.com.imarui.ima.authorization.core.domain.model.AuthorizationScope;
import java.util.*;

/** Immutable, bounded traversal; corrupt or incomplete hierarchies fail closed. */
public final class ScopeHierarchy {
  // Bound retained path references for unusually deep trees; traversal remains the same beyond this budget.
  private static final int PREPARED_PATH_NODE_LIMIT=100_000;
  private final Map<UUID, AuthorizationScope> scopes;
  private final Map<UUID,List<AuthorizationScope>> paths;

  public ScopeHierarchy(Collection<AuthorizationScope> scopes) {
    var indexed = new HashMap<UUID, AuthorizationScope>();
    for (var scope : scopes)
      if (indexed.put(scope.id(), scope) != null) throw new IllegalArgumentException("Duplicate Scope.");
    this.scopes = Map.copyOf(indexed);
    var prepared=new HashMap<UUID,List<AuthorizationScope>>();
    int retained=0;
    for (var scope : scopes) {
      var path=traverse(scope.id()); // Always validate, even when the retention budget is exhausted.
      if(path.size()<=PREPARED_PATH_NODE_LIMIT-retained) { prepared.put(scope.id(),path); retained+=path.size(); }
    }
    this.paths=Map.copyOf(prepared);
  }

  /** Includes the resource itself, ordered from resource to root. */
  public List<AuthorizationScope> ancestors(UUID id) {
    if(id==null) return List.of();
    var path=paths.get(id);
    return path==null?traverse(id):path;
  }

  private List<AuthorizationScope> traverse(UUID id) {
    var result = new ArrayList<AuthorizationScope>();
    var visited = new HashSet<UUID>();
    while (id != null) {
      if (!visited.add(id)) throw new IllegalArgumentException("Scope cycle.");
      var scope = scopes.get(id);
      if (scope == null) throw new IllegalArgumentException("Unknown Scope or ancestor.");
      result.add(scope);
      id = scope.parentId();
    }
    return List.copyOf(result);
  }

  public boolean covers(UUID ancestor, UUID resource) {
    return ancestors(resource).stream().anyMatch(s -> s.id().equals(ancestor));
  }

  public boolean active(UUID resource) {
    return ancestors(resource).stream().allMatch(s -> s.status() == AuthorizationScope.Status.ACTIVE);
  }

  public List<AuthorizationScope> descendants(UUID id) {
    ancestors(id);
    return scopes.values().stream().filter(s -> covers(id, s.id()))
        .sorted(Comparator.comparing(AuthorizationScope::code)).toList();
  }

  public void validateParent(UUID scopeId, UUID parentId) {
    ancestors(scopeId);
    if (parentId != null && (covers(scopeId, parentId) || !active(parentId)))
      throw new IllegalArgumentException("Parent would introduce a cycle or inactive ancestry.");
  }
}
