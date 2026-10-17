package br.com.imarui.ima.identity.core.application.port;

import br.com.imarui.ima.identity.core.domain.model.identity.IdentityId;
import br.com.imarui.ima.identity.core.domain.model.tenant.TenantId;

/** Requests must be durably recorded in the caller's transaction or fail it. */
public interface AccessRevocation {
    void revokeIdentity(IdentityId identityId);
    void revokeTenant(TenantId tenantId);
}
