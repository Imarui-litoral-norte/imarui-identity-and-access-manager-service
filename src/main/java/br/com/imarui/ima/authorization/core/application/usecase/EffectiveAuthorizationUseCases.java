package br.com.imarui.ima.authorization.core.application.usecase;

import br.com.imarui.ima.authorization.core.domain.model.AuthorizationScope;
import br.com.imarui.ima.authorization.core.domain.policy.EffectiveAuthorizationEvaluator;
import br.com.imarui.ima.authorization.core.repository.AuthorizationPolicyRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class EffectiveAuthorizationUseCases {
  private final AuthorizationPolicyRepository policies;
  private final EffectiveAuthorizationEvaluator evaluator=new EffectiveAuthorizationEvaluator();

  /** Internal trusted resource context; no external PDP endpoint is exposed in Round 1. */
  public EffectiveAuthorizationEvaluator.Decision evaluate(UUID identity,String permission,UUID scope) {
    return evaluator.evaluate(policies.current(),identity,permission,scope);
  }

  /** Existing unscoped IIAM administrative APIs only consume the migrated PLATFORM boundary. */
  public List<String> platformAuthorities(UUID identity) {
    var state=policies.current();
    return state.deprecatedPermissions().keySet().stream().filter(code ->
        evaluator.evaluate(state,identity,code,AuthorizationScope.PLATFORM_ID).allowed()).sorted().toList();
  }
}
