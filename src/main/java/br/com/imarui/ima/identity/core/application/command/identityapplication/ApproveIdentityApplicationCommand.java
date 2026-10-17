package br.com.imarui.ima.identity.core.application.command.identityapplication;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.IdentityApplicationId;
import br.com.imarui.ima.identity.core.domain.model.tenant.TenantId;
import java.util.Objects;
public record ApproveIdentityApplicationCommand(IdentityApplicationId identityApplicationId, TenantId tenantId, AffiliationSelection affiliation) {
    public ApproveIdentityApplicationCommand {
        Objects.requireNonNull(identityApplicationId); Objects.requireNonNull(tenantId); Objects.requireNonNull(affiliation);
    }
}
