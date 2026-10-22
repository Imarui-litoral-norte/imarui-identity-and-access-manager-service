package br.com.imarui.ima.authorization.core.domain.policy;

import java.util.Set;

/** Security administration only. Business permissions never receive a ROOT exception. */
public final class RootRecoveryEnvelope {
  private RootRecoveryEnvelope() {}
  public static final Set<String> CAPABILITIES=Set.of("AUTHORIZATION_SCOPE_MANAGE","AUTHORIZATION_ROLE_MANAGE",
      "AUTHORIZATION_ASSIGNMENT_MANAGE","AUTHORIZATION_DENY_MANAGE","AUTHORIZATION_DELEGATION_MANAGE",
      "AUTHORIZATION_CATALOG_MANAGE","AUTHORIZATION_ROOT_RECOVERY","AUTHORIZATION_AUDIT_READ","AUTHENTICATION_SERVICE_MANAGE");
}
