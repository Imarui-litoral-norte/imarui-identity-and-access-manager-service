package br.com.imarui.ima.identity.api.dto.tenant.response;

import br.com.imarui.ima.identity.core.application.result.tenant.TenantResult;
import br.com.imarui.ima.identity.core.domain.enums.tenant.TenantStatus;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        @io.swagger.v3.oas.annotations.media.Schema(description = "UUID estável do recurso.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        UUID id,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Código estável do Tenant, normalizado para maiúsculas.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        String code,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Nome de exibição do Tenant.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        String name,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Estado administrativo do recurso.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        TenantStatus status,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Criação em UTC.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Última alteração em UTC.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        Instant updatedAt,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Primeira ativação; nulo antes da ativação.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
        Instant activatedAt,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Desativação atual; nulo em ACTIVE/PENDING.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
        Instant disabledAt
) {

    public static TenantResponse from(
            @NotNull TenantResult result
    ) {
        return new TenantResponse(
                result.id(),
                result.code(),
                result.name(),
                result.status(),
                result.createdAt(),
                result.updatedAt(),
                result.activatedAt(),
                result.disabledAt()
        );
    }
}
