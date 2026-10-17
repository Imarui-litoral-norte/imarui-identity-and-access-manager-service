package br.com.imarui.ima.identity.core.domain.policy;

import br.com.imarui.ima.identity.core.domain.model.affiliation.Affiliation;
import br.com.imarui.ima.identity.core.domain.model.identity.Identity;
import br.com.imarui.ima.identity.core.domain.model.tenant.Tenant;

public final class AffiliationValidity {
    private AffiliationValidity() {}
    public static boolean isEffective(Affiliation affiliation, Identity identity, Tenant tenant) {
        return affiliation != null && identity != null && tenant != null
                && affiliation.getIdentityId().equals(identity.getId())
                && affiliation.getTenantId().equals(tenant.getId())
                && affiliation.isActive() && identity.isActive() && tenant.isActive();
    }
}
