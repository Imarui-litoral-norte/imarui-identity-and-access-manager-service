package br.com.imarui.ima.identity.core.application.command.identityapplication;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.IdentityApplicationId;
import br.com.imarui.ima.identity.core.domain.model.tenant.TenantId;
import java.util.Objects;
public record ApproveIdentityApplicationCommand(IdentityApplicationId identityApplicationId, TenantId tenantId, AffiliationSelection affiliation, br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind identityKind) {
    public ApproveIdentityApplicationCommand(IdentityApplicationId id, TenantId tenant, AffiliationSelection affiliation) { this(id, tenant, affiliation, null); }
    public ApproveIdentityApplicationCommand {
        Objects.requireNonNull(identityApplicationId); Objects.requireNonNull(tenantId); Objects.requireNonNull(affiliation);
    }
}
