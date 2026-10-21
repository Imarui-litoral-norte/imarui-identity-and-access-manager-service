package br.com.imarui.ima.identity.core.application.usecase.identityapplication;
import br.com.imarui.ima.identity.core.application.command.identityapplication.ApproveIdentityApplicationCommand;
import br.com.imarui.ima.identity.core.application.result.identityapplication.IdentityApplicationResult;
import br.com.imarui.ima.identity.core.application.port.*;
import br.com.imarui.ima.identity.core.application.service.*;
import br.com.imarui.ima.identity.core.application.exception.*;
import br.com.imarui.ima.identity.core.application.exception.tenant.TenantNotFoundException;
import br.com.imarui.ima.identity.core.domain.exception.identityapplication.IdentityApplicationReviewNotAllowedException;
import br.com.imarui.ima.identity.core.repository.*;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApproveIdentityApplicationUseCase {
    private final IdentityApplicationRepository applications;
    private final IdentityRepository identities;
    private final TenantRepository tenants;
    private final AffiliationRepository affiliations;
    private final CurrentActor currentActor;
    private final IdentityRegistrationLock registrationLock;
    private final IdentityApplicationConflicts conflicts;
    private final Clock clock;

    @Transactional
    public IdentityApplicationResult execute(ApproveIdentityApplicationCommand command) {
        var actor = currentActor.requireIdentityId();
        var application = applications.findByIdForUpdate(command.identityApplicationId()).orElseThrow(IdentityApplicationNotFoundException::new);
        if (!application.isPending()) throw new IdentityApplicationReviewNotAllowedException(application.getId(), application.getStatus());
        registrationLock.acquire(application);
        conflicts.check(application, false);
        var tenant = tenants.findByIdForUpdate(command.tenantId()).orElseThrow(() -> new TenantNotFoundException(command.tenantId()));
        if (!tenant.canProvisionIdentities()) throw new TenantUnavailableException();
        var now = clock.instant();
        var identity = identities.save(IdentityProvisioning.identity(application, now));
        if (application.getNickname() != null) { identity.assignNickname(application.getNickname()); identity = identities.save(identity); }
        var affiliation = affiliations.save(IdentityProvisioning.affiliation(command.affiliation(), tenant.getId(), identity.getId(), now, affiliations));
        application.approve(actor, identity.getId(), tenant.getId(), affiliation.getId(), now);
        return IdentityApplicationResult.from(applications.save(application));
    }
}
