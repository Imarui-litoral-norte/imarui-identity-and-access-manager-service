package br.com.imarui.ima.identity.api.dto.identityapplication.response;

import br.com.imarui.ima.identity.core.application.result.identityapplication.IdentityApplicationResult;
import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import br.com.imarui.ima.identity.core.domain.enums.identityapplication.IdentityApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Schema(description = "Solicitacao de cadastro de uma identidade.")
public record IdentityApplicationResponse(
        UUID id,
        IdentityKind type,
        String email,
        IdentityApplicationStatus status,
        IdentityApplicationDetailsResponse details,
        Instant requestedAt,
        Instant reviewedAt,
        UUID reviewedByIdentityId,
        String rejectionReason,
        UUID resolvedIdentityId,
        UUID assignedTenantId,
        UUID resultingAffiliationId
) {

    public static IdentityApplicationResponse from(
            IdentityApplicationResult result
    ) {
        Objects.requireNonNull(
                result,
                "IdentityApplicationResult must not be null."
        );

        return new IdentityApplicationResponse(
                result.id(),
                result.kind(),
                result.email(),
                result.status(),
                IdentityApplicationDetailsResponse.from(result.details()),
                result.requestedAt(),
                result.reviewedAt(),
                result.reviewedBy(),
                result.rejectionReason(),
                result.resolvedIdentityId(),
                result.assignedTenantId(),
                result.resultingAffiliationId()
        );
    }
}
