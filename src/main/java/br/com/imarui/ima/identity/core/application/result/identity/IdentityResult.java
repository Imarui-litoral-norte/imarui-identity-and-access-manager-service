package br.com.imarui.ima.identity.core.application.result.identity;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityStatus;
import br.com.imarui.ima.identity.core.domain.model.identity.Identity;

import java.time.Instant;
import java.util.UUID;

public record IdentityResult(
        UUID id,
        String nickname,
        IdentityKind kind,
        String primaryEmail,
        IdentityStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt,
        Instant disabledAt,
        IdentityDetailsResult details
) {

    public static IdentityResult from(Identity identity) {
        return new IdentityResult(
                identity.getId().value(),
                identity.getNickname(),
                identity.getKind(),
                identity.getPrimaryEmail()==null?null:identity.getPrimaryEmail().email().value(),
                identity.getStatus(),
                identity.getCreatedAt(),
                identity.getUpdatedAt(),
                identity.getActivatedAt(),
                identity.getDisabledAt(),
                IdentityDetailsResult.from(identity)
        );
    }
}
