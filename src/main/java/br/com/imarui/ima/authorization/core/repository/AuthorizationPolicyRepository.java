package br.com.imarui.ima.authorization.core.repository;

import br.com.imarui.ima.authorization.core.domain.policy.AuthorizationPolicyState;

public interface AuthorizationPolicyRepository {
  AuthorizationPolicyState current();
}
