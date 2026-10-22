package br.com.imarui.ima.authorization.core.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import br.com.imarui.ima.authorization.core.application.port.AuthorizationActor;
import br.com.imarui.ima.authorization.core.application.service.AuthorizationMutationGuard;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.domain.policy.AuthorizationPolicyState;
import br.com.imarui.ima.authorization.core.repository.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class RootEligibilityTest {
  @Test void initialGovernanceRequiresAnActiveCanonicalRootInAnActiveAdministrativeAncestry() {
    var now=Instant.parse("2026-10-22T12:00:00Z"); var identity=UUID.randomUUID();
    var scope=AuthorizationScope.create("REALM","Realm",null,now);
    var assignment=new RoleAssignment(UUID.randomUUID(),identity,1,scope.id(),true,now,now,0);
    var guard=new AuthorizationMutationGuard(mock(AuthorizationActor.class),mock(AuthorizationPolicyRepository.class),mock(AuthorizationMutationRepository.class));
    java.util.function.BiFunction<Role,AuthorizationScope,AuthorizationPolicyState> state=(role,boundary) ->
        new AuthorizationPolicyState(List.of(boundary),Map.of(1L,role),Map.of(),List.of(assignment),List.of(),List.of(),Map.of(),Map.of(),Set.of(identity),Set.of());
    var active=new Role(1L,"ROOT",Role.Ownership.GLOBAL,null,null,true,now,now,0);
    var inactive=new Role(1L,"ROOT",Role.Ownership.GLOBAL,null,null,false,now,now,0);
    var legacyLabel=new Role(1L," ROOT ",Role.Ownership.GLOBAL,null,null,true,now,now,0);
    assertTrue(guard.rootIdentity(state.apply(active,scope),identity));
    assertFalse(guard.rootIdentity(state.apply(inactive,scope),identity));
    assertFalse(guard.rootIdentity(state.apply(legacyLabel,scope),identity));
    assertFalse(guard.rootIdentity(state.apply(active,scope.revise(scope.name(),AuthorizationScope.Status.DISABLED,null,now)),identity));
  }
}
