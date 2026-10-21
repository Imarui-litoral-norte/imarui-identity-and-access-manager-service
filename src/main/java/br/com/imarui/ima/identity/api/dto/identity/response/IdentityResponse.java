package br.com.imarui.ima.identity.api.dto.identity.response;

import br.com.imarui.ima.identity.core.application.result.identity.IdentityResult;
import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityStatus;
import java.time.Instant;
import java.util.UUID;

public record IdentityResponse(
    @io.swagger.v3.oas.annotations.media.Schema(
            description = "UUID estável do recurso.",
            accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY,
            requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        UUID id,
    String nickname,
    @io.swagger.v3.oas.annotations.media.Schema(
            description = "Tipo que discrimina o bloco details.",
            accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY,
            requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        IdentityKind kind,
    @io.swagger.v3.oas.annotations.media.Schema(
            description =
                "E-mail obrigatório normalizado de contato, sem unicidade; não identifica login.",
            accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY,
            requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        String primaryEmail,
    @io.swagger.v3.oas.annotations.media.Schema(
            description = "Estado administrativo do recurso.",
            accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY,
            requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        IdentityStatus status,
    @io.swagger.v3.oas.annotations.media.Schema(
            description = "Criação em UTC.",
            accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY,
            requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        Instant createdAt,
    @io.swagger.v3.oas.annotations.media.Schema(
            description = "Última alteração em UTC.",
            accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY,
            requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        Instant updatedAt,
    @io.swagger.v3.oas.annotations.media.Schema(
            description = "Primeira ativação; nulo antes da ativação.",
            accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
        Instant activatedAt,
    @io.swagger.v3.oas.annotations.media.Schema(
            description = "Desativação atual; nulo em ACTIVE/PENDING.",
            accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
        Instant disabledAt,
    @io.swagger.v3.oas.annotations.media.Schema(
            description = "Dados específicos do tipo; não inclui credenciais.",
            accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY,
            requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        IdentityDetailsResponse details) {

  public static IdentityResponse from(IdentityResult result) {
    return new IdentityResponse(
        result.id(),
        result.nickname(),
        result.kind(),
        result.primaryEmail(),
        result.status(),
        result.createdAt(),
        result.updatedAt(),
        result.activatedAt(),
        result.disabledAt(),
        IdentityDetailsResponse.from(result.details()));
  }
}
