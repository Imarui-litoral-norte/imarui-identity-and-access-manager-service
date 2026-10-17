package br.com.imarui.ima.identity.api.dto.identity.response;

import br.com.imarui.ima.identity.core.application.result.identity.IdentityResult;
import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityStatus;

import java.time.Instant;
import java.util.UUID;

public record IdentityResponse(
        UUID id,
        IdentityKind kind,
        String primaryEmail,
        IdentityStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt,
        Instant disabledAt,
        IdentityDetailsResponse details
) {

    public static IdentityResponse from(IdentityResult result) {
        return new IdentityResponse(
                result.id(),
                result.kind(),
                result.primaryEmail(),
                result.status(),
                result.createdAt(),
                result.updatedAt(),
                result.activatedAt(),
                result.disabledAt(),
                IdentityDetailsResponse.from(result.details())
        );
    }
}
