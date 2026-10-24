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
        @io.swagger.v3.oas.annotations.media.Schema(description = "UUID estável do recurso.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        UUID id,
        String nickname,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Tipo que discrimina o bloco details.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        IdentityKind type,
        @io.swagger.v3.oas.annotations.media.Schema(description = "E-mail normalizado da solicitação.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        String email,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Estado administrativo do recurso.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        IdentityApplicationStatus status,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Dados específicos do tipo; não inclui credenciais.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        IdentityApplicationDetailsResponse details,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Instante UTC da solicitação.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        Instant requestedAt,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Instante de revisão; nulo enquanto PENDING.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
        Instant reviewedAt,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Ator da revisão resolvido pela autenticação; não é recebido no request.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
        UUID reviewedByIdentityId,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Motivo aparado; preenchido apenas em REJECTED.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
        String rejectionReason,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Identity criada na aprovação; nulo sem aprovação.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
        UUID resolvedIdentityId,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Tenant atribuído na aprovação.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
        UUID assignedTenantId,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Vínculo criado na aprovação.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
        UUID resultingAffiliationId,
        IdentityKind approvedKind
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
                result.nickname(),
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
                result.resultingAffiliationId(),
                result.approvedKind()
        );
    }
}
