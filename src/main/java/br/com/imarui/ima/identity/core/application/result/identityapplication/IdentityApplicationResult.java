package br.com.imarui.ima.identity.core.application.result.identityapplication;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import br.com.imarui.ima.identity.core.domain.enums.identityapplication.IdentityApplicationStatus;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.IdentityApplication;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IdentityApplicationResult(
        UUID id,
        String nickname,
        IdentityKind kind,
        String email,
        IdentityApplicationStatus status,
        IdentityApplicationDetailsResult details,
        Instant requestedAt,
        Instant reviewedAt,
        UUID reviewedBy,
        String rejectionReason,
        UUID resolvedIdentityId,
        UUID assignedTenantId,
        UUID resultingAffiliationId,
        IdentityKind approvedKind
) {

    public static IdentityApplicationResult from(
            IdentityApplication application
    ) {
        Objects.requireNonNull(
                application,
                "application cannot be null"
        );

        return new IdentityApplicationResult(
                application.getId().value(),
                application.getNickname(),
                application.getKind(),
                application.getEmail()==null?null:application.getEmail().value(),
                application.getStatus(),
                IdentityApplicationDetailsResult.from(
                        application.getDetails()
                ),
                application.getRequestedAt(),
                application.getReviewedAt(),
                application.getReviewedBy() == null
                        ? null
                        : application.getReviewedBy().value(),
                application.getRejectionReason(),
                application.getResolvedIdentityId() == null
                        ? null
                        : application.getResolvedIdentityId().value(),
                application.getAssignedTenantId() == null
                        ? null
                        : application.getAssignedTenantId().value(),
                application.getResultingAffiliationId() == null
                        ? null
                        : application.getResultingAffiliationId().value(),
                application.getApprovedKind()
        );
    }
}
