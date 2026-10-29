package br.com.imarui.ima.authorization.core.application.usecase;

import br.com.imarui.ima.authorization.core.domain.model.AuthorizationScope;
import br.com.imarui.ima.authorization.core.domain.policy.EffectiveAuthorizationEvaluator;
import br.com.imarui.ima.authorization.core.domain.policy.ScopeHierarchy;
import br.com.imarui.ima.authorization.core.repository.AuthorizationPolicyRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class EffectiveAuthorizationUseCases {
  private final AuthorizationPolicyRepository policies;
  private final EffectiveAuthorizationEvaluator evaluator=new EffectiveAuthorizationEvaluator();

  /** Internal trusted resource context; no external PDP endpoint is exposed in Round 1. */
  public EffectiveAuthorizationEvaluator.Decision evaluate(UUID identity,String permission,UUID scope) {
    return evaluator.evaluate(policies.current(),identity,permission,scope);
  }

  /** Filter before tenant search/count/pagination, using one current policy snapshot. */
  public Set<UUID> readableTenants(UUID identity) {
    var state=policies.current();
    final ScopeHierarchy hierarchy;
    try { hierarchy=new ScopeHierarchy(state.scopes()); }
    catch (IllegalArgumentException corruptHierarchy) {
      throw new org.springframework.security.access.AccessDeniedException("Tenant directory unavailable.");
    }
    if(state.scopes().stream().noneMatch(scope -> evaluator.evaluate(state,identity,"TENANT_READ",scope.id(),hierarchy).allowed()))
      throw new org.springframework.security.access.AccessDeniedException("Tenant directory requires TENANT_READ in an authorized context.");
    return state.tenantScopes().entrySet().stream()
        .filter(binding -> evaluator.evaluate(state,identity,"TENANT_READ",binding.getValue(),hierarchy).allowed())
        .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  /** Existing unscoped IIAM administrative APIs only consume the migrated PLATFORM boundary. */
  public List<String> platformAuthorities(UUID identity) {
    var state=policies.current();
    ScopeHierarchy hierarchy;
    try { hierarchy=new ScopeHierarchy(state.scopes()); }
    catch (IllegalArgumentException corruptHierarchy) { return List.of(); }
    return state.deprecatedPermissions().keySet().stream().filter(code ->
        evaluator.evaluate(state,identity,code,AuthorizationScope.PLATFORM_ID,hierarchy).allowed()).sorted().toList();
  }
}
