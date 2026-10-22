package br.com.imarui.ima.authorization.core.application.usecase;

import br.com.imarui.ima.authorization.api.module.SecurityAuditEvents;
import br.com.imarui.ima.authorization.core.application.port.AuthorizationActor;
import br.com.imarui.ima.authorization.core.application.service.AuthorizationMutationGuard;
import br.com.imarui.ima.authorization.core.domain.model.AuthorizationScope;
import br.com.imarui.ima.authorization.core.domain.policy.*;
import br.com.imarui.ima.authorization.core.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.REPEATABLE_READ,timeout=5)
public class SecurityAuditQueries {
  private final AuthorizationPolicyRepository policies;
  private final AuthorizationMutationGuard guard;
  private final AuthorizationActor actors;
  private final SecurityAuditRepository audits;
  private final SecurityAuditEvents events;
  public AuthorizationQueries.Page<SecurityAuditRepository.Entry> query(UUID scope,String event,UUID correlation,int page,int size) {
    AuthorizationQueries.page(List.of(),page,size);
    var state=policies.current(); var actor=actors.requireIdentityId(); var tree=new ScopeHierarchy(state.scopes());
    guard.require(state,actor,"AUTHORIZATION_AUDIT_READ",scope,tree);
    var evaluator=new EffectiveAuthorizationEvaluator();
    var potential=new AuthorizationPolicyState(state.scopes().stream().map(s -> new AuthorizationScope(s.id(),s.code(),s.name(),
        AuthorizationScope.Status.ACTIVE,s.parentId(),s.createdAt(),s.updatedAt(),s.version())).toList(),state.roles(),state.rolePermissions(),
        state.assignments(),state.denies(),state.delegations(),state.deprecatedPermissions(),state.tenantScopes(),state.activeIdentities(),state.activeTenants());
    var potentialTree=new ScopeHierarchy(potential.scopes());
    var permitted=new HashSet<UUID>();
    tree.descendants(scope).stream().filter(s -> evaluator.evaluate(potential,actor,"AUTHORIZATION_AUDIT_READ",s.id(),potentialTree).allowed()).forEach(s -> permitted.add(s.id()));
    events.recordInTransaction("AUDIT_EVENTS_READ",actor,"iiam",null,scope,"AUTHORIZED_AUDIT_READ",UUID.randomUUID());
    var result=audits.query(audits.includeHistoricalScopes(permitted),scope.equals(AuthorizationScope.PLATFORM_ID),event,correlation,page*size,size);
    return new AuthorizationQueries.Page<>(result.items(),result.total(),page,size);
  }
}
