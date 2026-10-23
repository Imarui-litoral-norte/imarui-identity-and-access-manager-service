package br.com.imarui.ima.authorization.core.domain;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.authorization.core.domain.model.AuthorizationScope;
import br.com.imarui.ima.authorization.core.domain.policy.ScopeHierarchy;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class ScopeHierarchyTest {
  private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");
  private AuthorizationScope node(String code, UUID parent) {
    return AuthorizationScope.create(code, code, parent, NOW);
  }

  @Test void inheritanceIsDownwardAndIndependentRootsAndSiblingsAreIsolated() {
    var root = node("ROOT_A", null);
    var regional = node("REGION", root.id());
    var local = node("LOCAL", regional.id());
    var sibling = node("SIBLING", regional.id());
    var other = node("ROOT_B", null);
    var tree = new ScopeHierarchy(List.of(root, regional, local, sibling, other));
    assertTrue(tree.covers(root.id(), local.id()));
    assertTrue(tree.covers(local.id(), local.id()));
    assertFalse(tree.covers(local.id(), regional.id()));
    assertFalse(tree.covers(local.id(), sibling.id()));
    assertFalse(tree.covers(root.id(), other.id()));
    assertEquals(List.of(local.id(), regional.id(), root.id()),
        tree.ancestors(local.id()).stream().map(AuthorizationScope::id).toList());
    assertEquals(3, tree.descendants(regional.id()).size());
  }

  @Test void reparentingPreservesIdentityAndChangesOnlyTheRelevantSubtree() {
    var root = node("A", null);
    var other = node("B", null);
    var local = node("LOCAL", root.id());
    var changed = local.revise("Renamed", local.status(), other.id(), NOW.plusSeconds(1));
    assertEquals(local.id(), changed.id());
    assertEquals(local.code(), changed.code());
    assertEquals(1, changed.version());
    var tree = new ScopeHierarchy(List.of(root, other, changed));
    assertFalse(tree.covers(root.id(), changed.id()));
    assertTrue(tree.covers(other.id(), changed.id()));
  }

  @Test void cyclesMissingParentsAndInvalidReparentingAreRejected() {
    var root = node("A", null);
    var child = node("B", root.id());
    var tree = new ScopeHierarchy(List.of(root, child));
    assertThrows(IllegalArgumentException.class, () -> tree.validateParent(root.id(), child.id()));
    assertThrows(IllegalArgumentException.class, () -> new ScopeHierarchy(List.of(child)));
    var cyclic = root.revise(root.name(), root.status(), child.id(), NOW);
    assertThrows(IllegalArgumentException.class, () -> new ScopeHierarchy(List.of(cyclic, child)));
    assertThrows(IllegalArgumentException.class, () -> tree.ancestors(UUID.randomUUID()));
  }

  @Test void disabledAncestorDisablesTheEntireSubtreeWithoutDestroyingIt() {
    var root = node("A", null);
    var child = node("B", root.id());
    var disabled = root.revise(root.name(), AuthorizationScope.Status.DISABLED, null, NOW);
    var tree = new ScopeHierarchy(List.of(disabled, child));
    assertFalse(tree.active(child.id()));
    assertTrue(tree.covers(root.id(), child.id()));
  }

  @Test void invalidValuesAndChangesToTheRecoveryBoundaryAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> node("lowercase", null));
    assertThrows(IllegalArgumentException.class, () -> AuthorizationScope.create("CODE", " ", null, NOW));
    var platform = new AuthorizationScope(AuthorizationScope.PLATFORM_ID, "IIAM_PLATFORM", "IIAM",
        AuthorizationScope.Status.ACTIVE, null, NOW, NOW, 0);
    assertThrows(IllegalArgumentException.class, () -> platform.revise("IIAM",
        AuthorizationScope.Status.DISABLED, null, NOW));
    assertThrows(IllegalArgumentException.class, () -> platform.revise("IIAM",
        AuthorizationScope.Status.ACTIVE, UUID.randomUUID(), NOW));
  }

  @Test void preparedPathsAreImmutableAndNeverSharedAcrossReparentingOrLifecycleRevisions() {
    var root=node("ROOT",null); var other=node("OTHER",null); var child=node("CHILD",root.id());
    var old=new ScopeHierarchy(List.of(root,other,child));
    var moved=child.revise(child.name(),child.status(),other.id(),NOW.plusSeconds(1));
    var disabled=other.revise(other.name(),AuthorizationScope.Status.DISABLED,null,NOW.plusSeconds(1));
    var next=new ScopeHierarchy(List.of(root,disabled,moved));
    assertTrue(old.covers(root.id(),child.id())); assertTrue(old.active(child.id()));
    assertFalse(next.covers(root.id(),child.id())); assertTrue(next.covers(other.id(),child.id()));
    assertFalse(next.active(child.id()));
    assertThrows(UnsupportedOperationException.class,() -> old.ancestors(child.id()).clear());
    assertThrows(IllegalArgumentException.class,() -> next.active(UUID.randomUUID()));
  }
  @Test void unusuallyDeepTreesRemainValidatedWhenPreparedPathBudgetIsExceeded() {
    var scopes=new ArrayList<AuthorizationScope>(); UUID parent=null;
    for(int i=0;i<600;i++) { var scope=node("DEEP_"+i,parent); scopes.add(scope); parent=scope.id(); }
    var tree=new ScopeHierarchy(scopes);
    assertEquals(600,tree.ancestors(parent).size()); assertTrue(tree.covers(scopes.get(0).id(),parent));
    assertThrows(IllegalArgumentException.class,() -> tree.ancestors(UUID.randomUUID()));
    var broken=scopes.get(599).revise("Broken",AuthorizationScope.Status.ACTIVE,UUID.randomUUID(),NOW);
    scopes.set(599,broken);
    assertThrows(IllegalArgumentException.class,() -> new ScopeHierarchy(scopes));
  }
}
